import os
from contextlib import asynccontextmanager
from datetime import datetime

from fastapi import FastAPI, Request
from pydantic import BaseModel, Field

from app.features import build_row
from app.model import load_model

MODEL_DIR = os.getenv("MODEL_DIR", os.path.join(os.path.dirname(__file__), "..", "models"))
MODEL_VERSION = os.getenv("MODEL_VERSION", "v1")


class ScoreRequest(BaseModel):
    amount: float = Field(gt=0)
    customer_avg_amount: float | None = Field(default=None, ge=0, description="null for a first transaction")
    seconds_since_last_txn: float | None = Field(default=None, ge=0)
    distance_from_last_km: float | None = Field(default=None, ge=0)
    txns_last_hour: int = Field(default=0, ge=0, description="earlier transactions in the last hour")
    is_new_device: bool
    timestamp: datetime = Field(description="ISO-8601; assumed UTC when no zone is given")


class ScoreResponse(BaseModel):
    score: float = Field(description="probability of fraud, 0..1")
    model_version: str


@asynccontextmanager
async def lifespan(app: FastAPI):
    # The model is loaded once at startup and never trained here. A missing model stops the service.
    app.state.model = load_model(MODEL_DIR, MODEL_VERSION)
    yield


app = FastAPI(title="Fraud ML sidecar", lifespan=lifespan)


@app.post("/score", response_model=ScoreResponse)
def score(body: ScoreRequest, request: Request) -> ScoreResponse:
    model = request.app.state.model
    row = build_row(
        body.amount,
        body.customer_avg_amount,
        body.seconds_since_last_txn,
        body.distance_from_last_km,
        body.txns_last_hour,
        body.is_new_device,
        body.timestamp,
    )
    return ScoreResponse(score=round(model.score(row), 4), model_version=model.version)


@app.get("/health")
def health(request: Request) -> dict:
    return {"status": "ok", "model_version": request.app.state.model.version}


@app.get("/model")
def model_info(request: Request) -> dict:
    return request.app.state.model.metadata
