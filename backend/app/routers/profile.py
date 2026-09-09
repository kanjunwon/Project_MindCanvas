from fastapi import APIRouter, Depends, HTTPException

from app.dependencies import get_current_user_id
from app.schemas.profile import ProfileUpdateRequest, ProfileResponse
from app.repositories.user_repository import get_user_by_id, update_profile

router = APIRouter()


@router.get("/users/me/profile", response_model=ProfileResponse)
def get_my_profile(user_id: int = Depends(get_current_user_id)):
    user = get_user_by_id(user_id)
    if user is None:
        raise HTTPException(status_code=404, detail="사용자를 찾을 수 없습니다")
    return ProfileResponse(
        status="success",
        glasses=user.get("glasses", False),
        bangs=user.get("bangs", True),
        hair_length=user.get("hair_length", "medium"),
        hair_color=user.get("hair_color", "black"),
    )


@router.patch("/users/me/profile", response_model=ProfileResponse)
def update_my_profile(req: ProfileUpdateRequest, user_id: int = Depends(get_current_user_id)):
    update_profile(
        user_id,
        glasses=req.glasses,
        bangs=req.bangs,
        hair_length=req.hair_length,
        hair_color=req.hair_color,
    )
    return ProfileResponse(
        status="success",
        glasses=req.glasses,
        bangs=req.bangs,
        hair_length=req.hair_length,
        hair_color=req.hair_color,
    )