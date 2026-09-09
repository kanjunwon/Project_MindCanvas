from dotenv import load_dotenv
load_dotenv()

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from app.routers import diary, stats, personal_test, auth, profile, account

app = FastAPI(title="감정 서가 API")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(diary.router)
app.include_router(stats.router)
app.include_router(auth.router)
app.include_router(personal_test.router)
app.include_router(profile.router)
app.include_router(account.router)


@app.get("/")
def health_check():
    return {"status": "ok"}
