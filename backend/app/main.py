from pathlib import Path

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from fastapi.staticfiles import StaticFiles
from sqlalchemy import inspect, text
from app.database.database import engine, Base
from app import models
from app.routes import router

app = FastAPI()

origins = [
    "http://localhost:3000",
    "http://127.0.0.1:3000",
    "https://fit-gpt-i3co.vercel.app",
    "https://www.fitgpt.tech"
]

app.add_middleware(
    CORSMiddleware,
    allow_origins=origins,
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

Base.metadata.create_all(bind=engine)


def _ensure_runtime_schema() -> None:
    """Apply minimal additive schema changes for local environments without migrations."""
    inspector = inspect(engine)
    table_names = set(inspector.get_table_names())
    if "users" not in table_names:
        return

    user_columns = {column["name"] for column in inspector.get_columns("users")}
    if "avatar_url" not in user_columns:
        with engine.begin() as connection:
            connection.execute(text("ALTER TABLE users ADD COLUMN avatar_url VARCHAR"))
    if "google_id" not in user_columns:
        with engine.begin() as connection:
            connection.execute(text("ALTER TABLE users ADD COLUMN google_id VARCHAR"))
            connection.execute(text("CREATE UNIQUE INDEX IF NOT EXISTS ix_users_google_id ON users (google_id)"))
    if "auth_provider" not in user_columns:
        with engine.begin() as connection:
            connection.execute(text("ALTER TABLE users ADD COLUMN auth_provider VARCHAR DEFAULT 'email'"))

    if "clothing_items" in table_names:
        clothing_columns = {column["name"] for column in inspector.get_columns("clothing_items")}
        pending_alters: list[str] = []
        if "layer_type" not in clothing_columns:
            pending_alters.append("ALTER TABLE clothing_items ADD COLUMN layer_type VARCHAR")
        if "is_one_piece" not in clothing_columns:
            pending_alters.append("ALTER TABLE clothing_items ADD COLUMN is_one_piece BOOLEAN DEFAULT FALSE")
        if "set_identifier" not in clothing_columns:
            pending_alters.append("ALTER TABLE clothing_items ADD COLUMN set_identifier VARCHAR")
        if "style_tags_json" not in clothing_columns:
            pending_alters.append("ALTER TABLE clothing_items ADD COLUMN style_tags_json VARCHAR DEFAULT '[]'")
        if "season_tags_json" not in clothing_columns:
            pending_alters.append("ALTER TABLE clothing_items ADD COLUMN season_tags_json VARCHAR DEFAULT '[]'")
        if "colors_json" not in clothing_columns:
            pending_alters.append("ALTER TABLE clothing_items ADD COLUMN colors_json VARCHAR DEFAULT '[]'")
        if "occasion_tags_json" not in clothing_columns:
            pending_alters.append("ALTER TABLE clothing_items ADD COLUMN occasion_tags_json VARCHAR DEFAULT '[]'")
        if "accessory_type" not in clothing_columns:
            pending_alters.append("ALTER TABLE clothing_items ADD COLUMN accessory_type VARCHAR")
        if pending_alters:
            with engine.begin() as connection:
                for sql in pending_alters:
                    connection.execute(text(sql))


_ensure_runtime_schema()

app.include_router(router)
uploads_dir = Path("uploads")
uploads_dir.mkdir(parents=True, exist_ok=True)
app.mount("/uploads", StaticFiles(directory=uploads_dir), name="uploads")

@app.get("/")
def root():
    return {"message": "FitGPT backend is running"}
