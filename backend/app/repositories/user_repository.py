from app.database import supabase

TABLE_NAME = "users"


def create_user(email: str, password_hash: str, nickname: str = None):
    if supabase is None:
        raise RuntimeError("DB 연결 안 됨 - 회원가입 불가")
    response = supabase.table(TABLE_NAME).insert({
        "email": email,
        "password_hash": password_hash,
        "nickname": nickname,
    }).execute()
    return response.data[0] if response.data else None


def get_user_by_email(email: str):
    if supabase is None:
        return None
    response = supabase.table(TABLE_NAME).select("*").eq("email", email).execute()
    return response.data[0] if response.data else None


def get_user_by_id(user_id: int):
    if supabase is None:
        return None
    response = supabase.table(TABLE_NAME).select("*").eq("id", user_id).execute()
    return response.data[0] if response.data else None


def update_profile(user_id: int, glasses: bool, bangs: bool, hair_length: str, hair_color: str):
    if supabase is None:
        return None
    response = (
        supabase.table(TABLE_NAME)
        .update({"glasses": glasses, "bangs": bangs, "hair_length": hair_length, "hair_color": hair_color})
        .eq("id", user_id)
        .execute()
    )
    return response.data[0] if response.data else None


def update_account(user_id: int, gender: str = None, job: str = None, birth_date=None):
    if supabase is None:
        return None
    fields = {}
    if gender is not None:
        fields["gender"] = gender
    if job is not None:
        fields["job"] = job
    if birth_date is not None:
        fields["birth_date"] = str(birth_date)
    if not fields:
        return get_user_by_id(user_id)
    response = supabase.table(TABLE_NAME).update(fields).eq("id", user_id).execute()
    return response.data[0] if response.data else None


def update_password(user_id: int, password_hash: str):
    if supabase is None:
        return None
    response = (
        supabase.table(TABLE_NAME).update({"password_hash": password_hash}).eq("id", user_id).execute()
    )
    return response.data[0] if response.data else None


def delete_user(user_id: int):
    if supabase is None:
        return None
    # DB에 FK CASCADE가 안 걸려있어서 (diary_entries.user_id가 text 타입) 여기서 직접
    # 연쇄 삭제 처리. 계정탈퇴 결정(2026-09-09)에 따라 관련 데이터 다 지우고 마지막에 계정 삭제.
    user_id_str = str(user_id)
    supabase.table("diary_entries").delete().eq("user_id", user_id_str).execute()
    supabase.table("personal_test_results").delete().eq("user_id", user_id_str).execute()
    response = supabase.table(TABLE_NAME).delete().eq("id", user_id).execute()
    return response.data[0] if response.data else None