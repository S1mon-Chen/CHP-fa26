ARG BASE_IMAGE=python:3.12-slim
FROM ${BASE_IMAGE}
RUN pip install --no-cache-dir pytest==8.3.5 && useradd --uid 10001 --create-home student
USER 10001:10001
WORKDIR /work
ENV PYTHONDONTWRITEBYTECODE=1 PYTEST_DISABLE_PLUGIN_AUTOLOAD=1
CMD ["python", "-m", "pytest", "-q", "-p", "no:cacheprovider", "tests"]
