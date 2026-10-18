"""Turns one transaction's behavioural inputs into the numbers the model sees.

Training and serving both call build_row(), so the two can never drift apart.
Missing history (a customer's first transaction) is passed as NaN: LightGBM
handles missing values natively, so no made-up placeholder values are needed.
"""
import math
from datetime import datetime, timezone

NAN = float("nan")

FEATURES = [
    "amount",
    "amount_vs_avg",        # amount / customer's average amount (NaN if no history)
    "seconds_since_last",   # time since the customer's previous transaction
    "distance_from_last_km",
    "txns_last_hour",       # earlier transactions in the last hour (not counting this one)
    "is_new_device",
    "hour_sin",             # time of day as a circle, so 23:00 and 01:00 are close
    "hour_cos",
    "is_weekend",
]


def build_row(
    amount: float,
    customer_avg_amount: float | None,
    seconds_since_last_txn: float | None,
    distance_from_last_km: float | None,
    txns_last_hour: int,
    is_new_device: bool,
    timestamp: datetime,
) -> list[float]:
    if timestamp.tzinfo is None:
        timestamp = timestamp.replace(tzinfo=timezone.utc)
    ts = timestamp.astimezone(timezone.utc)
    hour = ts.hour + ts.minute / 60.0
    angle = 2 * math.pi * hour / 24.0

    has_avg = customer_avg_amount is not None and customer_avg_amount > 0
    return [
        float(amount),
        amount / customer_avg_amount if has_avg else NAN,
        NAN if seconds_since_last_txn is None else float(seconds_since_last_txn),
        NAN if distance_from_last_km is None else float(distance_from_last_km),
        float(txns_last_hour),
        1.0 if is_new_device else 0.0,
        math.sin(angle),
        math.cos(angle),
        1.0 if ts.weekday() >= 5 else 0.0,
    ]
