"""
app/services/image_prompt_service.py
재유 스펙(핸드오프 문서) 그대로 구현.

파이프라인: 일기 생성(LLM 1번째 호출) -> 감정분석(KoBERT) -> 이미지 프롬프트 변환(LLM 2번째 호출) -> ComfyUI

기존 일기 생성용 LLaMA를 그대로 재사용함 (별도 모델 불필요, _generate_once 그대로 씀).
"""
import json
import re
import time

SYSTEM_PROMPT = """당신은 한국어 일기를 Stable Diffusion(Illustrious XL) 이미지 생성용 영어 danbooru 태그 프롬프트로 변환하는 전문가입니다.

[입력으로 주어지는 것]
- 일기 본문 (한국어)
- 감정 라벨 (KoBERT 분석 결과)
- Who, What, When, Where 메타데이터

[출력 규칙]
1. 반드시 영어 danbooru 태그 형식으로 출력하세요. 자연어 문장이 아니라 쉼표로 구분된 태그 나열입니다.
2. 모든 태그 끝에는 반드시 "gamjeong style"을 붙이세요.

3. 인원수 규칙 (중요 — 반드시 정확한 숫자로 명시할 것):
   - "multiple people", "multiple girls", "multiple boys", "group of people" 같은 상한 없는 표현은 절대 사용 금지.
   - 1명: "1girl, solo" 또는 "1boy, solo"
   - 커플: "1girl, young adult woman, 1boy, young adult man, couple" (나이대와 관계 반드시 명시. "1girl, 1boy"만 쓰면 엄마+아들처럼 오인될 수 있음)
   - 2~6명(친구/동료/가족 등): 반드시 성별+정확한 숫자로 명시. 예: "3girls, 2boys", "2girls, 1boy"
   - 인원 태그는 프롬프트의 맨 앞부분에 배치하세요 (danbooru 계열 모델은 앞쪽 태그의 영향력이 더 큼).
   - 일기에 "친구들", "가족들", "동기들"처럼 정확한 인원수가 특정되지 않은 경우에도, 문맥상 합리적인 숫자를 추정해서 성별+숫자로 명시하되 절대 6명을 넘기지 마세요.
   - 실제 일기 내용상 인원이 6명을 초과하는 경우(예: 반 전체, 대가족 모임, 큰 파티)에도 이미지에는 최대 6명까지만 반영하고, 나머지 인원은 언급하지 않습니다.

4. 배경에 사람이 많은 장소 규칙 (놀이공원, 워터파크, 축제, 콘서트, 대형 행사장 등):
   - 전경(foreground)에 얼굴이 명확히 그려지는 인물은 최대 2~3명으로 제한: 성별+숫자 명시 (예: "2girls")
   - 배경 군중은 얼굴을 그리게 하는 표현을 쓰지 말고, "out of focus background", "bokeh", "blurred crowd silhouettes" 같은 표현만 사용
   - 배경 군중에 대해 "multiple people in background"처럼 사람 숫자나 사람이라는 개념을 구체화하는 표현은 쓰지 마세요.

5. 부모님/중장년층 등장 시 "mature female, mother" 또는 "mature male, father"를 반드시 포함하세요.

6. 감정 라벨에 따라 표정/분위기 태그를 추가하세요:
   - 긍정 감정: "smile, blush, warm lighting"
   - 부정 감정: "frown, tired expression, dim lighting" 등

7. 장소, 시간대, 소품은 일기 내용에서 구체적으로 추출해서 포함하세요.

8. 화풍 강조 태그("flat color, cel shading, line art, illustration")는 인원수가 4명 이상이거나 2인 이상 정면 구도일 때 반드시 추가하세요.

[출력 형식 - 반드시 이 JSON 형식으로만 답하세요]
{
  "positive": "여기에 영어 태그 프롬프트",
  "negative": "bad anatomy, extra limbs, missing limbs, deformed arm, malformed hands, extra fingers, missing fingers, fused fingers, mutated hands, disfigured, distorted, blurry, low quality, photorealistic, realistic, photo, 3d render, text, watermark, gibberish text, chinese text, chinese characters, kanji, hanzi, hanja, extra people, extra person, additional people, crowd, too many people, duplicate character, clone, multiple views, split screen"
}

[예시 1] - 1인
입력: 일기="퇴근 후 지쳐서 집에 왔는데 고양이가 골골송을 부르며 반겨줬다", Who=["반려동물","고양이"], 감정="힐링됨"
출력: {"positive": "1girl, young adult woman, solo, cat, cuddling, sitting, living room, evening, warm indoor lighting, relieved expression, gamjeong style", "negative": "bad anatomy, extra limbs, missing limbs, deformed arm, malformed hands, extra fingers, missing fingers, fused fingers, mutated hands, disfigured, distorted, blurry, low quality, photorealistic, realistic, photo, 3d render, text, watermark, gibberish text, chinese text, chinese characters, kanji, hanzi, hanja, extra people, extra person, additional people, crowd, too many people, duplicate character, clone, multiple views, split screen"}

[예시 2] - 부모님
입력: 일기="아빠와 함께 셀프 세차장에서 고압수 세차를 했다", Who=["가족","아빠"], 감정="흐뭇함"
출력: {"positive": "1boy, young adult man, mature male, father, high pressure water gun, washing car, self car wash, morning, casual clothes, smiling, gamjeong style", "negative": "bad anatomy, extra limbs, missing limbs, deformed arm, malformed hands, extra fingers, missing fingers, fused fingers, mutated hands, disfigured, distorted, blurry, low quality, photorealistic, realistic, photo, 3d render, text, watermark, gibberish text, chinese text, chinese characters, kanji, hanzi, hanja, extra people, extra person, additional people, crowd, too many people, duplicate character, clone, multiple views, split screen"}

[예시 3] - 인원수 특정 안 된 "친구들" (6명 상한 적용)
입력: 일기="동아리 뒤풀이로 친구들이랑 노래방에 갔다", Who=["친구"], 감정="신남"
출력: {"positive": "3girls, 3boys, young adults, karaoke room, holding microphones, singing, colorful lighting, laughing, flat color, cel shading, line art, illustration, gamjeong style", "negative": "bad anatomy, extra limbs, missing limbs, deformed arm, malformed hands, extra fingers, missing fingers, fused fingers, mutated hands, disfigured, distorted, blurry, low quality, photorealistic, realistic, photo, 3d render, text, watermark, gibberish text, chinese text, chinese characters, kanji, hanzi, hanja, extra people, extra person, additional people, crowd, too many people, duplicate character, clone, multiple views, split screen"}

[예시 4] - 사람 많은 장소 (전경/배경 분리)
입력: 일기="친구들이랑 놀이공원에 가서 롤러코스터를 탔다", Who=["친구"], 감정="신남"
출력: {"positive": "2girls, foreground, riding roller coaster, amusement park, screaming, excited expression, blurred crowd silhouettes in background, bokeh, sunny day, gamjeong style", "negative": "bad anatomy, extra limbs, missing limbs, deformed arm, malformed hands, extra fingers, missing fingers, fused fingers, mutated hands, disfigured, distorted, blurry, low quality, photorealistic, realistic, photo, 3d render, text, watermark, gibberish text, chinese text, chinese characters, kanji, hanzi, hanja, extra people, extra person, additional people, crowd, too many people, duplicate character, clone, multiple views, split screen"}

[예시 5] - 대가족 모임 (6명 초과 케이스, 대표인물+사물로 축소)
입력: 일기="외할머니 칠순 잔치에 온 가족과 친척들이 모였다", Who=["가족","엄마","아빠","형제,자매"], 감정="행복함"
출력: {"positive": "1girl, young adult woman, elderly woman, grandmother, sitting together at table, birthday cake, candles, banquet table with many dishes, paper decorations, warm celebratory lighting, gamjeong style", "negative": "bad anatomy, extra limbs, missing limbs, deformed arm, malformed hands, extra fingers, missing fingers, fused fingers, mutated hands, disfigured, distorted, blurry, low quality, photorealistic, realistic, photo, 3d render, text, watermark, gibberish text, chinese text, chinese characters, kanji, hanzi, hanja, extra people, extra person, additional people, crowd, too many people, duplicate character, clone, multiple views, split screen"}"""

# 파싱 완전히 실패했을 때 쓰는 최후 안전값 (v3, 인원수 초과 방지 항목 추가된 최신본)
FALLBACK_NEGATIVE = (
    "bad anatomy, extra limbs, missing limbs, deformed arm, malformed hands, "
    "extra fingers, missing fingers, fused fingers, mutated hands, disfigured, "
    "distorted, blurry, low quality, photorealistic, realistic, photo, 3d render, "
    "text, watermark, gibberish text, chinese text, chinese characters, kanji, hanzi, hanja, "
    "extra people, extra person, additional people, crowd, too many people, "
    "duplicate character, clone, multiple views, split screen"
)

JSON_PATTERN = re.compile(r'\{.*"positive"\s*:.*"negative"\s*:.*\}', re.DOTALL)


def _build_translation_prompt(diary_text: str, who, emotion: str, where: str, when: str) -> str:
    who_str = ", ".join(who) if isinstance(who, list) else str(who)
    user_content = f'일기="{diary_text}", Who={who_str}, 감정="{emotion}", 장소="{where}", 시간="{when}"'
    return (
        f"A chat between a curious user and an artificial intelligence assistant.\n\n"
        f"{SYSTEM_PROMPT}\n\n"
        f"Human: {user_content}\n"
        f"Assistant:\n"
    )


def _extract_json(text: str) -> dict | None:
    # 모델이 JSON 앞뒤로 잡담을 붙이는 경우가 있어서, 정규식으로 JSON 블록만 뽑아냄
    match = JSON_PATTERN.search(text)
    if not match:
        return None
    try:
        return json.loads(match.group(0))
    except json.JSONDecodeError:
        return None


def _fallback_prompt(top_emotion: str) -> dict:
    # LLM 변환이 완전히 실패했을 때, 최소한 이미지 생성 자체는 되도록 하는 안전값
    return {
        "positive": f"1girl, solo, {top_emotion}, gamjeong style",
        "negative": FALLBACK_NEGATIVE,
    }


def translate_to_image_prompt(diary_text: str, who, emotion: str, where: str, when: str) -> dict:
    """
    일기 텍스트 -> {"positive": "...", "negative": "..."} 영어 danbooru 태그.
    기존 일기 생성용 LLaMA를 재사용 (별도 모델 로딩 없음).
    """
    from app.services.llama_service import _generate_once, MOCK_MODE

    if MOCK_MODE:
        return _fallback_prompt(emotion)

    start = time.time()
    prompt_str = _build_translation_prompt(diary_text, who, emotion, where, when)

    result = None
    for attempt in range(1, 3):  # 최대 2번만 재시도 (JSON 파싱 실패 대비, 너무 오래 끌지 않게)
        raw = _generate_once(prompt_str, temperature=0.3, max_new_tokens=200)
        result = _extract_json(raw)
        if result and "positive" in result and "negative" in result:
            break
        print(f"  [이미지 프롬프트 변환] {attempt}번째 시도 JSON 파싱 실패: {raw[:80]}...")
        result = None

    elapsed = time.time() - start
    print(f"  [이미지 프롬프트 변환] 소요시간: {elapsed:.1f}초")

    if result is None:
        print("  [이미지 프롬프트 변환] 최종 실패, 안전값으로 대체")
        return _fallback_prompt(emotion)

    return result