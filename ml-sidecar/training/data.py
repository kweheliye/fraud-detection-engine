"""Synthetic card-transaction generator.

This is DEMO data so the project works out of the box. Real fraud data is private,
so replace it with your own history for production use (see README).

What it simulates
- Customers with their own typical amount, home city, devices and *time of day*.
  Activity hours are spread across the whole day, so a normal evening or night-time
  purchase is never mistaken for fraud just because of the hour.
- Legitimate behaviour that looks suspicious: trips to another city, occasional big
  purchases, a new phone, a quick burst of shopping.
- Fraud events (account takeover and card testing), including some "stealthy" ones
  that look almost normal, so the task is realistic and not perfectly solvable.
"""
import math
import random
from datetime import datetime, timedelta, timezone

from app.features import build_row

START = datetime(2026, 1, 1, tzinfo=timezone.utc)

CITIES = {  # name: (lat, lon)
    "dubai": (25.20, 55.27), "london": (51.51, -0.13), "new_york": (40.71, -74.01),
    "mumbai": (19.08, 72.88), "singapore": (1.35, 103.82), "sydney": (-33.87, 151.21),
    "nairobi": (-1.29, 36.82), "sao_paulo": (-23.55, -46.63),
}


def haversine_km(a: tuple[float, float], b: tuple[float, float]) -> float:
    lat1, lon1, lat2, lon2 = map(math.radians, (*a, *b))
    h = math.sin((lat2 - lat1) / 2) ** 2 + math.cos(lat1) * math.cos(lat2) * math.sin((lon2 - lon1) / 2) ** 2
    return 6371 * 2 * math.asin(math.sqrt(h))


def _legit_time_of_day(rng: random.Random, centre: float, spread: float) -> float:
    """Hour of day around the customer's habit, wrapping around midnight."""
    return (rng.gauss(centre, spread)) % 24


class _Customer:
    def __init__(self, rng: random.Random):
        self.avg = math.exp(rng.gauss(math.log(55), 0.7))
        self.home = rng.choice(list(CITIES))
        self.devices = [f"dev-{rng.getrandbits(32):08x}" for _ in range(rng.randint(1, 3))]
        self.hour_centre = rng.uniform(8, 24)      # morning people ... night owls
        self.hour_spread = rng.uniform(2.5, 5.5)   # broad: covers most of the day
        self.rate = rng.uniform(0.4, 2.5)          # transactions per day
        # running history, used to compute features exactly like the Java side will
        self.amounts: list[float] = []
        self.times: list[datetime] = []
        self.seen_devices: set[str] = set()
        self.location = CITIES[self.home]

    def features(self, amount, when, device, location) -> dict:
        avg = sum(self.amounts) / len(self.amounts) if self.amounts else None
        since = (when - self.times[-1]).total_seconds() if self.times else None
        dist = haversine_km(self.location, location) if self.times else None
        last_hour = sum(1 for t in self.times[-30:] if 0 <= (when - t).total_seconds() < 3600)
        return dict(
            amount=amount, customer_avg_amount=avg, seconds_since_last_txn=since,
            distance_from_last_km=dist, txns_last_hour=last_hour,
            is_new_device=device not in self.seen_devices, timestamp=when,
        )

    def record(self, amount, when, device, location):
        self.amounts.append(amount)
        self.times.append(when)
        self.seen_devices.add(device)
        self.location = location


def generate(n_customers: int = 2000, days: int = 60, seed: int = 7) -> list[dict]:
    """Returns rows sorted by time: {'features': [...], 'label': 0/1, 'timestamp': datetime}."""
    rng = random.Random(seed)
    events: list[tuple[datetime, int, dict]] = []  # (when, customer index, spec)
    customers = [_Customer(rng) for _ in range(n_customers)]

    for ci, c in enumerate(customers):
        trip_until, trip_city = None, None
        for day in range(days):
            day_start = START + timedelta(days=day)
            if trip_until is None and rng.random() < 0.02:           # starts a trip
                trip_until, trip_city = day + rng.randint(1, 4), rng.choice(list(CITIES))
            if trip_until is not None and day >= trip_until:
                trip_until = None
            city = trip_city if trip_until is not None else c.home

            for _ in range(_poisson(rng, c.rate)):
                hour = _legit_time_of_day(rng, c.hour_centre, c.hour_spread)
                when = day_start + timedelta(hours=hour)
                big = rng.random() < 0.03
                amount = c.avg * math.exp(rng.gauss(0, 0.55)) * (rng.uniform(3, 9) if big else 1)
                device = c.devices[0] if rng.random() < 0.7 else rng.choice(c.devices)
                if rng.random() < 0.01:                              # bought a new phone
                    device = f"dev-{rng.getrandbits(32):08x}"
                    c.devices.append(device)
                events.append((when, ci, dict(amount=amount, device=device, city=city, label=0)))
                if rng.random() < 0.04:                              # quick shopping burst
                    for k in range(rng.randint(1, 3)):
                        events.append((when + timedelta(minutes=rng.randint(1, 15) * (k + 1)), ci,
                                       dict(amount=amount * rng.uniform(0.3, 1.5), device=device, city=city, label=0)))

            if rng.random() < 0.005:                                 # a fraud event hits this customer
                _add_fraud(rng, events, ci, c, day_start)

    events.sort(key=lambda e: e[0])
    rows = []
    for when, ci, spec in events:
        c = customers[ci]
        location = CITIES[spec["city"]]
        f = c.features(spec["amount"], when, spec["device"], location)
        rows.append({"features": build_row(**f), "label": spec["label"], "timestamp": when})
        c.record(spec["amount"], when, spec["device"], location)
    return rows


def _add_fraud(rng, events, ci, c, day_start):
    when = day_start + timedelta(hours=rng.uniform(0, 24))
    kind = rng.random()
    far_city = rng.choice([n for n in CITIES if n != c.home])
    if kind < 0.15:      # stealthy: known device, normal-looking amount, home city
        events.append((when, ci, dict(amount=c.avg * rng.uniform(0.8, 2.5), device=c.devices[0], city=c.home, label=1)))
        return
    device = f"dev-{rng.getrandbits(32):08x}" if rng.random() < 0.85 else c.devices[0]
    city = far_city if rng.random() < 0.7 else c.home
    if kind < 0.45:      # card testing: many tiny charges in quick succession
        for k in range(rng.randint(5, 12)):
            events.append((when + timedelta(seconds=rng.randint(5, 40) * (k + 1)), ci,
                           dict(amount=rng.uniform(0.5, 6), device=device, city=city, label=1)))
    else:                # account takeover: a few large purchases
        for k in range(rng.randint(1, 5)):
            events.append((when + timedelta(minutes=rng.randint(1, 20) * (k + 1)), ci,
                           dict(amount=c.avg * rng.uniform(3, 25), device=device, city=city, label=1)))


def _poisson(rng: random.Random, lam: float) -> int:
    limit, k, p = math.exp(-lam), 0, 1.0
    while True:
        p *= rng.random()
        if p <= limit:
            return k
        k += 1
