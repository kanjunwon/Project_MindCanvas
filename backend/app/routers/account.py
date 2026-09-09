from fastapi import APIRouter, Depends, HTTPException

from app.dependencies import get_current_user_id
from app.schemas.account import AccountInfoResponse, AccountUpdateRequest
from app.schemas.auth import PasswordChangeRequest
from app.repositories.user_repository import (
    get_user_by_id, update_account, update_password, delete_user,
)
from app.repositories.diary_repository import get_diary_by_user
from app.services.auth_service import hash_password, verify_password

router = APIRouter()


@router.get("/users/me/account", response_model=AccountInfoResponse)
def get_my_account(user_id: int = Depends(get_current_user_id)):
    user = get_user_by_id(user_id)
    if user is None:
        raise HTTPException(status_code=404, detail="사용자를 찾을 수 없습니다")
    return AccountInfoResponse(
        status="success",
        email=user["email"],
        nickname=user.get("nickname"),
        gender=user.get("gender"),
        job=user.get("job"),
        birth_date=user.get("birth_date"),
    )


@router.patch("/users/me/account", response_model=AccountInfoResponse)
def update_my_account(req: AccountUpdateRequest, user_id: int = Depends(get_current_user_id)):
    update_account(user_id, gender=req.gender, job=req.job, birth_date=req.birth_date)
    user = get_user_by_id(user_id)
    return AccountInfoResponse(
        status="success",
        email=user["email"],
        nickname=user.get("nickname"),
        gender=user.get("gender"),
        job=user.get("job"),
        birth_date=user.get("birth_date"),
    )


@router.patch("/users/me/password")
def change_password(req: PasswordChangeRequest, user_id: int = Depends(get_current_user_id)):
    user = get_user_by_id(user_id)
    if user is None or not verify_password(req.current_password, user["password_hash"]):
        raise HTTPException(status_code=401, detail="현재 비밀번호가 일치하지 않습니다")

    new_hash = hash_password(req.new_password)
    update_password(user_id, new_hash)
    return {"status": "success"}


@router.delete("/users/me")
def delete_my_account(user_id: int = Depends(get_current_user_id)):
    delete_user(user_id)
    return {"status": "success"}


@router.get("/users/me/stats")
def get_my_settings_stats(user_id: int = Depends(get_current_user_id)):
    """
    설정 화면 상단 요약 카드용 ("감정 기록을 시작한 지 N Days", "기록한 감정 N Emotion").
    /stats/daily, /stats/monthly랑은 다른 용도라 별도 엔드포인트로 분리.
    """
    diaries = get_diary_by_user(str(user_id)) or []
    if not diaries:
        return {"status": "success", "days_since_start": 0, "emotion_count": 0}

    from datetime import datetime, timezone

    created_dates = [d["created_at"] for d in diaries if d.get("created_at")]
    earliest = min(created_dates) if created_dates else None
    days_since_start = 0
    if earliest:
        earliest_dt = datetime.fromisoformat(earliest.replace("Z", "+00:00"))
        days_since_start = (datetime.now(timezone.utc) - earliest_dt).days

    distinct_emotions = {d["top_emotion"] for d in diaries if d.get("top_emotion")}

    return {
        "status": "success",
        "days_since_start": days_since_start,
        "emotion_count": len(distinct_emotions),
    }