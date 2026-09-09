# 감정 서가 백엔드

## 셋업

```bash
cd backend
python -m venv .venv          # 또는 conda(base) 환경 그대로 사용해도 무방
source .venv/bin/activate
pip install -r requirements.txt
```

⚠️ **GPU 서버(RunPod 등)에서 실제 모델(`MOCK_MODE=false`)을 돌릴 거면, `requirements.txt`만으로 충분한지 먼저 확인할 것.** 개발 로컬 PC들이 지금까지 `MOCK_MODE=true`로만 테스트해왔다면 `torch`/`transformers`/`accelerate`/`sentencepiece`/`bitsandbytes` 같은 GPU 추론 패키지가 `requirements.txt`에 안 잡혀있을 수 있음 (lazy import라 로컬에서 한 번도 실제로 안 불렸으면 애초에 설치 자체가 안 됐을 가능성). RunPod에서 처음 셋업할 때:
```bash
pip install -r requirements.txt
python -c "import torch; print(torch.cuda.is_available())"   # False면 GPU용 torch 재설치 필요
# CUDA 버전에 맞는 torch가 필요하면:
# pip install torch --index-url https://download.pytorch.org/whl/cu121  (CUDA 버전은 RunPod 이미지에 맞게)
```

## `.env` 만들기

`backend` 폴더에 `.env` 파일 만들고 채우기 (`.env.example` 참고):

```
MOCK_MODE=false
SUPABASE_URL=
SUPABASE_KEY=
HF_TOKEN=
JWT_SECRET=
COMFYUI_URL=
```

- `MOCK_MODE=true`: 실제 AI 모델(LLaMA/KoBERT) 없이 가짜 응답으로 API 테스트 (GPU 없는 환경에서 개발할 때 사용)
- `MOCK_MODE=false`: 실제 모델 로딩, GPU 필요
- `SUPABASE_URL` / `SUPABASE_KEY`: 재유한테 받은 값
- `HF_TOKEN`: Hugging Face 토큰 (LLaMA 모델 다운로드용)
- `JWT_SECRET`: 로그인 토큰 서명용 랜덤 문자열
- `COMFYUI_URL`: ComfyUI 서버 주소 (예: `http://127.0.0.1:8188`, RunPod에서 FastAPI랑 ComfyUI 같은 머신에 띄우면 localhost로 충분)

## KoBERT 모델 파일 준비 (git에는 코드만 있고, 가중치는 별도)

1. 종현이가 공유한 구글드라이브에서 `emotion24_inference.zip` 다운로드
2. 압축 풀고 안의 `model/emotion24-bert` 폴더를 `backend/kobert_model/emotion24-bert`로 복사
3. `emotion_list.py`는 이미 `app/emotion_list.py`로 레포에 포함되어 있음 (모델 출력 순서 매핑용, 절대 수정 금지 - 학습 시 순서 그대로 유지해야 함)

```bash
mkdir -p kobert_model
cp -r [압축 푼 경로]/model/emotion24-bert kobert_model/
```

## ComfyUI 준비 (이미지 생성용)

1. ComfyUI를 API 모드로 실행: `python main.py --listen 0.0.0.0 --port 8188`
2. Illustrious XL 체크포인트 + `gamjeong_illustrious_v1.safetensors` LoRA가 ComfyUI 모델 폴더에 있어야 함
3. 워크플로우는 `backend/app/comfyui_workflow.json`에 API 형식으로 저장되어 있음 (ComfyUI에서 "Enable Dev mode Options" 켜고 "Save (API Format)"으로 export한 것)

## 서버 실행

```bash
python -m uvicorn app.main:app --host 0.0.0.0 --port 8000
```

**`--reload` 옵션은 절대 쓰지 말 것.** 개발 중 재시도 로직이 여러 번 도는 상황에서 서버가 불안정해지는 원인으로 확인됨 (모델이 이미 GPU에 로딩된 상태에서 파일 변경 감지로 재시작되면서 응답이 끊기는 문제로 추정). 로컬 `MOCK_MODE=true` 개발 중에만 `--reload` 써도 무방, `MOCK_MODE=false`(실 모델) 환경에서는 절대 금지.

`http://127.0.0.1:8000/docs` 에서 API 테스트 가능 (Swagger UI). 로그인 필요한 API는 우측 상단 **Authorize** 버튼으로 토큰(`Bearer ` 없이 값만) 등록하면 이후 요청에 자동 적용됨.

## 폴더 구조

```
app/
├── main.py                       # FastAPI 진입점 (CORS, load_dotenv 순서 주의)
├── config.py
├── database.py                   # Supabase 클라이언트 (키 없으면 DB 스킵 모드로 자동 전환)
├── dependencies.py               # JWT 토큰 → user_id 추출 (HTTPBearer, Swagger Authorize 연동)
├── emotion_taxonomy.py           # 24개 감정 분류 체계 + 색상 팔레트 (재유)
├── emotion_list.py               # KoBERT 모델 출력 ID→감정명 매핑 (종현, 수정 금지)
├── personal_test_questions.py    # 퍼스널 검사 19문항(HSP 13 + LOT-R 6) 원문
├── schemas/
│   ├── diary.py                  # DiaryResponse에 top_emotion/emotion_scores/sentiment_score 포함
│   ├── stats.py
│   ├── auth.py                   # 회원가입 검증(비밀번호 8자+영문/숫자), 비밀번호 변경 스키마 포함
│   ├── profile.py                # 아바타(안경/앞머리/머리길이/머리색)
│   ├── account.py                # 계정 설정(성별/직업/생년월일)
│   └── personal_test.py
├── routers/
│   ├── diary.py                  # 일기 생성 시 KoBERT 감정 분석 + SD3 이미지 생성까지 같이 실행
│   ├── stats.py
│   ├── auth.py                   # signup/login
│   ├── profile.py                # GET/PATCH /users/me/profile (아바타)
│   ├── account.py                # 계정 설정, 비밀번호 변경, 탈퇴, 설정화면 요약통계
│   └── personal_test.py          # 검사 제출 + GET /personal-test/status
├── services/
│   ├── llama_service.py          # 호미 - 프롬프트, 검증, 안전 템플릿 폴백 포함
│   ├── image_prompt_service.py   # 일기 텍스트 → 영어 danbooru 태그 프롬프트 변환 (LLM 2차 호출)
│   ├── kobert_service.py         # 실제 24개 감정분류 모델 연동 완료
│   ├── ad3_service.py            # (미사용, sd3_service.py로 대체 - 삭제 예정)
│   ├── sd3_service.py            # 재유 - ComfyUI API 호출 방식, 아바타 속성 프롬프트 반영 완료
│   ├── stats_service.py          # 호미
│   └── auth_service.py           # 호미
├── models/
│   ├── llama_loader.py           # torch/transformers lazy import (Mock 모드에서 안 불림)
│   ├── kobert_loader.py          # 마찬가지로 lazy import, cuda:1 고정
│   └── sd3_loader.py             # ComfyUI 워크플로우 JSON 로더 + LoRA 파일명 sanity check (재유)
└── repositories/
    ├── diary_repository.py
    ├── user_repository.py        # 프로필/계정 수정, 비밀번호 변경, 탈퇴(연쇄삭제) 포함
    └── personal_test_repository.py
```

## DB 스키마

- `supabase_schema.sql`: `diary_entries` (감정 분석 컬럼 포함)
- `users_table.sql`: `users` (이메일/비밀번호/닉네임)
- `users`에 이후 추가된 컬럼(재유, 2026-09): `glasses`, `bangs`, `hair_length`, `hair_color`(아바타), `gender`, `job`, `birth_date`(계정 설정) — 별도 SQL 파일 없이 SQL Editor에서 직접 `alter table`로 추가함
- `personal_test_results`: 재유가 별도 생성

**RLS는 전부 꺼둔 상태로 운영.** Supabase Auth가 아닌 자체 JWT 로그인 방식이라, 클라이언트가 Supabase를 직접 안 건드리고 항상 백엔드를 거쳐서만 DB에 접근함 → user_id 검증은 백엔드 코드가 전담. 새 테이블 만들 때마다 기본값이 RLS 켜짐이라 매번 꺼줘야 함:
```sql
alter table [테이블명] disable row level security;
```

## API 엔드포인트

### 인증
- `POST /signup` — 이메일/비밀번호(8~64자, 영문+숫자 포함)/닉네임(2~20자)로 회원가입, JWT 토큰 즉시 발급
- `POST /login` — 로그인, JWT 토큰 발급

> ⚠️ **아래 API는 전부 `Authorization: Bearer <토큰>` 헤더 필수.** `user_id`를 body/path로 직접 안 받고 토큰에서만 가져옴.

### 일기
- `POST /generate-diary`

**Request**
```json
{
  "what": "카페에서 과제 하기", "why": "...", "who": ["혼자"],
  "when": "주말 오후 2시", "where": "집 앞 카페"
}
```
**Response**
```json
{
  "status": "success",
  "generated_diary": "...",
  "validation_failed": false,
  "top_emotion": "편안한",
  "emotion_scores": { "행복한": 0.05, "편안한": 0.4, ... 24개 전부 },
  "sentiment_score": 0.6,
  "image_url": "https://.../diary-images/xxxx.png"
}
```
일기 생성(LLM) → KoBERT 감정 분석 → 이미지 프롬프트 변환(LLM) → ComfyUI 이미지 생성 → Supabase Storage 업로드 → DB 저장까지 한 번에 처리됨. 로그인한 사용자의 아바타 설정(안경/앞머리/머리길이/머리색)이 이미지 생성 프롬프트에 자동 반영됨.

- `GET /diaries/{user_id}` — ⚠️ **아직 JWT 미적용, TODO**: URL의 user_id가 로그인 여부와 무관하게 그대로 조회됨. 다른 API들이랑 통일해서 JWT 기반으로 바꿔야 함 (다음 작업)

### 통계
- `GET /stats/daily/{user_id}?date=YYYY-MM-DD`, `GET /stats/monthly/{user_id}?year=&month=` — 24개 감정 기준 통계, `emotion_distribution`에 재유가 정한 8개 중분류 색상 자동 매핑됨. ⚠️ 이것도 `/diaries/{user_id}`와 마찬가지로 JWT 미적용 상태 (TODO)

### 퍼스널 감정 검사
- `POST /personal-test` — HSP 13문항 + LOT-R 6문항 응답 제출. 결과는 사용자에게 노출하지 않고 내부 저장만 함 (`weight_profile` 계산 로직은 미완성, TODO로 남아있음). 재제출하면 새 row로 쌓이고 가장 최근 것이 "현재" 결과로 취급됨
- `GET /personal-test/status` — 이 사용자가 검사를 완료했는지 확인. **회원가입 직후 필수로 검사받도록 프론트에서 이걸로 분기 처리하기로 함** (2026-09-09 결정)

### 아바타 프로필
- `GET/PATCH /users/me/profile` — 안경(`glasses`)/앞머리(`bangs`)/머리길이(`hair_length`: short/medium/long)/머리색(`hair_color`: 자유 문자열, 한국어/영어 웬만큼 매핑됨)

### 계정 설정
- `GET/PATCH /users/me/account` — 성별/직업/생년월일
- `PATCH /users/me/password` — 현재 비밀번호 확인 후 변경
- `DELETE /users/me` — 계정탈퇴 (diary_entries, personal_test_results까지 연쇄 삭제)
- `GET /users/me/stats` — 설정 화면 상단 요약카드용 ("감정 기록 시작한 지 N Days", "기록한 감정 N Emotion")

---

## 🔧 트러블슈팅 노트 (겪었던 문제들, 다음에 또 겪지 않기 위한 기록)

### "Name or service not known" / DB 저장 실패
학교 네트워크가 Supabase 도메인을 막아둔 경우가 있음. `curl -I [SUPABASE_URL]`로 먼저 확인. 안 되면 핫스팟으로 네트워크 전환해서 재시도.

### "521: Web server is down" (Supabase)
Supabase 무료 플랜은 오래 안 쓰면 프로젝트가 자동으로 일시정지(Paused)됨. **프로젝트 소유자만 Restore 가능** (협업자 권한으로는 안 됨) - 재유한테 요청해야 함. Restore 후에도 DNS/서버가 완전히 뜨는 데 몇 분 걸릴 수 있음.

### 회원가입 시 500 에러 - bcrypt/passlib 버전 충돌
`passlib`(업데이트 멈춘 라이브러리)이 최신 `bcrypt`(4.1+)의 내부 속성(`__about__`)을 못 찾아서 `AttributeError` → 이어서 `ValueError: password cannot be longer than 72 bytes` 에러로 이어짐. 비밀번호 길이 문제가 아니라 **라이브러리 버전 궁합 문제**. 해결:
```bash
pip install "bcrypt==4.0.1"
```
`requirements.txt`에 `bcrypt==4.0.1`로 버전 고정되어 있어야 함 - 새로 셋업할 때 이 줄이 빠져있으면 다시 겪을 수 있음.

### 안드로이드 앱 - 로컬 백엔드 연결
- `ApiClient.kt`의 `BASE_URL`이 `10.0.2.2`(에뮬레이터 전용, 호스트 PC의 127.0.0.1을 가리킴)로 되어있으면 에뮬레이터에서만 작동
- 실제 기기나 다른 PC에서 접속하려면 실제 네트워크 IP로 교체 필요 (`hostname -I` 또는 `ip route get 8.8.8.8`로 확인)
- 학교 Wi-Fi는 기기 간 통신을 막아두는 경우(AP Isolation)가 많음 → 안 되면 핫스팟으로 우회
- 방화벽 포트 개방 필요: `sudo ufw allow 8000` (ComfyUI 쓰면 `8188`도)
- **2026-09-09부로 로그인 필수 API가 늘어남** - `generate-diary`, `personal-test` 등 호출 시 `Authorization: Bearer <토큰>` 헤더 빠뜨리면 401 남. 로그인/회원가입 성공 시 받은 토큰을 저장해뒀다가 이후 요청마다 실어 보내야 함

### 리눅스에서 Android 앱 빌드 (Android Studio 없이)
```bash
sudo apt install openjdk-17-jdk
# Android SDK cmdline-tools 설치 후
export ANDROID_HOME=~/android-sdk
export PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator
sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0"
cd frontend
chmod +x gradlew   # 권한 없으면 빌드 자체가 안 됨
./gradlew assembleDebug
```
에뮬레이터가 필요하면 `system-images;android-34;google_apis;x86_64` 추가 설치 후 `avdmanager create avd`.

### 프론트-백엔드 연결 관련 (진행 중 이슈)
`ApiClient`는 만들어져 있어도, 실제 화면(버튼 클릭 등)에서 `emotionApi.generateDiary(...)`를 호출하는 코드가 아직 연결 안 된 경우가 있었음. 코드에 API 클라이언트가 존재하는 것과, 실제로 호출되는 것은 다르니 `grep -rn "emotionApi\." app/src`로 실제 호출부가 있는지 확인 필요.

### LLaMA 프롬프트 - 방향(소유격) 헷갈림
"친구네 집"처럼 소유격이 들어간 장소 정보를 줬을 때, 모델이 방향을 반대로 뒤집어서 쓰는 경우 발견 (예: "친구 집에 갔다"를 "친구가 우리 집에 왔다"로 왜곡). `llama_service.py`에 명시적 규칙 + few-shot 예시 추가해서 해결. 비슷한 소유격 상황(부모님 댁, 회사 등) 새로 추가할 때도 방향 명확한 예시를 few-shot에 넣어주는 게 안전함.

### LLaMA 프롬프트 - 지어내기 위험도 구분
"지어내지 마라"를 카테고리 나열식으로 계속 추가하는 방식은 한계가 있음(무한히 늘어남). 대신 "저위험(흔한 배경 묘사는 허용) vs 고위험(결과/판단, 술, 식사시간대, 대화내용은 절대 금지)"으로 구분해서 지시하는 방식으로 전환. 닫힌 어휘(술 종류, 식사시간대, 관계호칭)는 코드로 자동 검증 가능하지만, 열린 카테고리(대화 주제, 결과 판단 등)는 프롬프트+낮은 temperature+안전 템플릿 폴백으로만 감수 가능. 완전히 0%는 안 됨 - 10.8B 모델의 구조적 한계로 보임.

### KoBERT - MAX_LEN 잘림 버그 (중요, 발견 및 수정)
`infer.py` 원본의 `MAX_LEN=64`를 그대로 썼더니, 실제 일기(3~5문장, 90~250자)는 토큰 수가 64를 훌쩍 넘어서(예: 93토큰) **뒷부분이 통째로 잘려나가는 문제** 발견. 특히 일기가 부정적인 사건으로 시작해서 긍정적으로 마무리되는 경우, 그 긍정적 마무리가 잘려서 안 보이니 감정이 완전히 반대로 분류됨 (예: "피곤했지만... 재밌었다" 같은 문장에서 "재밌었다"가 잘려 "피곤한"으로만 판단).

`kobert_loader.py`에서 `MAX_LEN`을 128로 늘려서 개선됨. **단, 모델이 학습할 때도 64로 잘렸다면 65번째 토큰 이후는 학습 안 된 위치라 완벽하지 않을 수 있음 - 종현이한테 학습 시 max_length도 확인 요청 필요.** 여전히 완벽하진 않고(예: "피곤한"에 과민 반응하는 경향 일부 남아있음), 재학습 시 더 긴 max_length로 다시 돌리면 개선 가능성 있음.

### RunPod 등 GPU 클라우드로 옮길 때 체크리스트
1. `pip install -r requirements.txt` 후 `python -c "import torch; print(torch.cuda.is_available())"`로 GPU 인식 확인 (False면 CUDA용 torch 재설치)
2. `kobert_model/emotion24-bert` 폴더 업로드 (git에 안 올라가 있음)
3. ComfyUI 별도 실행 + `.env`의 `COMFYUI_URL`을 실제 주소로 설정
4. `--reload` 절대 금지 (위 서버 실행 섹션 참고)
5. 상시로 켜둘 필요 없음 - 테스트하는 시간에만 켰다 끄는 방식으로 비용 절감 (시간당 과금)
