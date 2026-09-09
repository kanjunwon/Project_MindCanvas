from pydantic import BaseModel, EmailStr, Field, field_validator
from typing import Optional


class SignupRequest(BaseModel):
    email: EmailStr
    password: str = Field(min_length=8, max_length=64)
    nickname: Optional[str] = Field(default=None, min_length=2, max_length=20)

    @field_validator("password")
    @classmethod
    def password_complexity(cls, v: str) -> str:
        if not any(c.isdigit() for c in v):
            raise ValueError("비밀번호에 숫자가 최소 1개 포함되어야 합니다")
        if not any(c.isalpha() for c in v):
            raise ValueError("비밀번호에 영문자가 최소 1개 포함되어야 합니다")
        return v


class LoginRequest(BaseModel):
    email: EmailStr
    password: str


class TokenResponse(BaseModel):
    status: str
    access_token: str
    user_id: int
    nickname: Optional[str] = None


class PasswordChangeRequest(BaseModel):
    current_password: str
    new_password: str = Field(min_length=8, max_length=64)

    @field_validator("new_password")
    @classmethod
    def password_complexity(cls, v: str) -> str:
        if not any(c.isdigit() for c in v):
            raise ValueError("비밀번호에 숫자가 최소 1개 포함되어야 합니다")
        if not any(c.isalpha() for c in v):
            raise ValueError("비밀번호에 영문자가 최소 1개 포함되어야 합니다")
        return v