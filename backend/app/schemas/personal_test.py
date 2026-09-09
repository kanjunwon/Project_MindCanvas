from typing import Dict
from pydantic import BaseModel, field_validator

from app.personal_test_questions import QUESTION_IDS


class PersonalTestSubmitRequest(BaseModel):
    answers: Dict[str, int]  # {"1": 4, "2": 2, ..., "19": 5}, 1~5 척도
    # user_id는 더 이상 여기 안 받음 - 로그인 토큰에서만 가져옴 (2026-09-09 변경)

    @field_validator("answers")
    @classmethod
    def validate_answers(cls, v: Dict[str, int]) -> Dict[str, int]:
        expected = set(str(qid) for qid in QUESTION_IDS)
        given = set(v.keys())
        if given != expected:
            missing = expected - given
            extra = given - expected
            raise ValueError(
                f"19문항 전체 응답이 필요합니다. 누락: {sorted(missing)}, 불필요: {sorted(extra)}"
            )
        for qid, score in v.items():
            if not (1 <= score <= 5):
                raise ValueError(f"문항 {qid}의 점수는 1~5 사이여야 합니다 (받은 값: {score})")
        return v


class PersonalTestSubmitResponse(BaseModel):
    status: str  # "success"