from fastapi import APIRouter, Depends, HTTPException

from app.dependencies import get_current_user_id
from app.schemas.personal_test import PersonalTestSubmitRequest, PersonalTestSubmitResponse
from app.repositories.personal_test_repository import save_personal_test_result
from app.database import supabase

router = APIRouter()


@router.post("/personal-test", response_model=PersonalTestSubmitResponse)
def submit_personal_test(req: PersonalTestSubmitRequest, user_id: int = Depends(get_current_user_id)):
    try:
        save_personal_test_result(
            user_id=str(user_id),
            answers=req.answers,
            weight_profile=None,
        )
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"검사 결과 저장 중 오류 발생: {e}")

    return PersonalTestSubmitResponse(status="success")


@router.get("/personal-test/status")
def get_personal_test_status(user_id: int = Depends(get_current_user_id)):
    if supabase is None:
        return {"status": "success", "completed": False}

    response = (
        supabase.table("personal_test_results")
        .select("id, completed_at")
        .eq("user_id", str(user_id))
        .order("completed_at", desc=True)
        .limit(1)
        .execute()
    )
    completed = bool(response.data)
    return {
        "status": "success",
        "completed": completed,
        "last_completed_at": response.data[0]["completed_at"] if completed else None,
    }
