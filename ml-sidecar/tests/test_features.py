import math
from datetime import datetime, timezone

from app.features import FEATURES, build_row

NOON = datetime(2026, 3, 9, 12, 0, tzinfo=timezone.utc)  # a Monday


def row_as_dict(**overrides):
    args = dict(amount=100, customer_avg_amount=50, seconds_since_last_txn=60, distance_from_last_km=10,
                txns_last_hour=2, is_new_device=True, timestamp=NOON)
    args.update(overrides)
    return dict(zip(FEATURES, build_row(**args)))


def test_values_follow_the_feature_order():
    row = row_as_dict()
    assert row["amount"] == 100
    assert row["amount_vs_avg"] == 2.0
    assert row["seconds_since_last"] == 60
    assert row["distance_from_last_km"] == 10
    assert row["txns_last_hour"] == 2
    assert row["is_new_device"] == 1.0
    assert row["is_weekend"] == 0.0


def test_missing_history_becomes_nan():
    row = row_as_dict(customer_avg_amount=None, seconds_since_last_txn=None, distance_from_last_km=None)
    assert math.isnan(row["amount_vs_avg"])
    assert math.isnan(row["seconds_since_last"])
    assert math.isnan(row["distance_from_last_km"])


def test_zero_average_is_treated_as_no_history():
    assert math.isnan(row_as_dict(customer_avg_amount=0)["amount_vs_avg"])


def test_time_of_day_wraps_around_midnight():
    late = row_as_dict(timestamp=datetime(2026, 3, 9, 23, 30, tzinfo=timezone.utc))
    early = row_as_dict(timestamp=datetime(2026, 3, 10, 0, 30, tzinfo=timezone.utc))
    assert abs(late["hour_sin"] - early["hour_sin"]) < 0.3
    assert abs(late["hour_cos"] - early["hour_cos"]) < 0.01


def test_weekend_flag_and_timezone_conversion():
    saturday = row_as_dict(timestamp=datetime(2026, 3, 14, 12, 0, tzinfo=timezone.utc))
    assert saturday["is_weekend"] == 1.0
    naive = row_as_dict(timestamp=datetime(2026, 3, 14, 12, 0))  # no zone: treated as UTC
    assert naive["is_weekend"] == 1.0
