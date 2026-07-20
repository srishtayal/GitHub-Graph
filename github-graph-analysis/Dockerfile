FROM python:3.11-slim AS base

WORKDIR /app

COPY pyproject.toml pyproject.toml
COPY app app

RUN pip install --no-cache-dir .

FROM base AS test

COPY tests tests

RUN python -m unittest discover -s tests -p "test_*.py"

FROM base AS runtime

EXPOSE 8000

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
