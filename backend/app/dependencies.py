"""
app/dependencies.py
JWT 토큰(Authorization: Bearer <token>)에서 로그인된 user_id를 추출하는 의존성.
"""
from fastapi import Depends, HTTPException
from fastapi.security import HTTPBearer, HTTPAuthorizationCredentials
from app.services.auth_service import decode_access_token

bearer_scheme = HTTPBearer()


def get_current_user_id(credentials: HTTPAuthorizationCredentials = Depends(bearer_scheme)) -> int:
    token = credentials.credentials
    user_id = decode_access_token(token)
    if user_id is None:
        raise HTTPException(status_code=401, detail="유효하지 않거나 만료된 토큰입니다")
    return user_id
