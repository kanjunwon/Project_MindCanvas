from typing import Literal
from pydantic import BaseModel


class ProfileUpdateRequest(BaseModel):
    glasses: bool
    bangs: bool
    hair_length: Literal["short", "medium", "long"]
    hair_color: str  # 프론트 드롭다운에서 고른 값 그대로 (예: "black", "brown", "blonde" 등)


class ProfileResponse(BaseModel):
    status: str
    glasses: bool
    bangs: bool
    hair_length: str
    hair_color: str