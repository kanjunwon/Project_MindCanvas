from typing import Optional
from datetime import date
from pydantic import BaseModel


class AccountInfoResponse(BaseModel):
    status: str
    email: str
    nickname: Optional[str] = None
    gender: Optional[str] = None
    job: Optional[str] = None
    birth_date: Optional[date] = None


class AccountUpdateRequest(BaseModel):
    gender: Optional[str] = None
    job: Optional[str] = None
    birth_date: Optional[date] = None
