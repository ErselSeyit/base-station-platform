# Base Station Platform - Makefile
# One-command deployment and management

.PHONY: help docker_start docker_stop docker_restart docker_logs docker_init_db docker_cleanup docker_safe_restart k8s_deploy k8s_undeploy k8s_status k8s_logs test build clean

# Default target
help:
	@echo "Base Station Platform - Available Commands:"
	@echo ""
	@echo "Docker Compose (Local Development):"
	@echo "  make docker_start        - Start all services with Docker Compose"
	@echo "  make docker_stop         - Stop all services"
	@echo "  make docker_restart      - Restart all services"
	@echo "  make docker_safe_restart - Safe restart with cleanup (RECOMMENDED)"
	@echo "  make docker_cleanup      - Clean up zombie containers and ports"
	@echo "  make docker_logs         - Follow all container logs"
	@echo "  make docker_init_db      - Initialize databases manually"
	@echo ""
	@echo "Kubernetes (Production):"
	@echo "  make k8s_deploy       - One-click deploy to Kubernetes"
	@echo "  make k8s_init_db      - Initialize all K8s databases (REQUIRED after first deploy)"
	@echo "  make k8s_undeploy     - Remove from Kubernetes"
	@echo "  make k8s_status       - Show deployment status"
	@echo "  make k8s_logs         - Tail all pod logs"
	@echo ""
	@echo "Development:"
	@echo "  make build            - Build all Maven packages"
	@echo "  make test             - Run all tests"
	@echo "  make clean            - Clean build artifacts"
	@echo ""

# ============================================
# Docker Compose Commands
# ============================================

docker_start:
	@echo "🔍 Pre-start validation..."
	@./scripts/validate-clean-state.sh
	@echo ""
	@echo "Starting all services with Docker Compose..."
	docker compose up -d
	@echo "Waiting for services to initialize..."
	@sleep 5
	docker compose ps

docker_stop:
	@echo "Stopping all services..."
	docker compose down

docker_restart:
	@echo "Restarting all services..."
	docker compose down
	docker compose up -d

docker_logs:
	docker compose logs -f

docker_init_db:
	@echo "Initializing databases..."
	@echo "[1/2] Seeding auth database..."
	docker exec -i postgres-auth psql -U postgres -d authdb < init-db/auth-seed.sql
	@echo "[2/2] Seeding MongoDB metrics..."
	docker cp init-db/mongodb-seed.js mongodb:/tmp/
	docker exec mongodb mongosh --username admin --password admin --authenticationDatabase admin --file /tmp/mongodb-seed.js
	@echo "✓ Database initialization complete"

docker_cleanup:
	@echo "Cleaning up zombie containers and port conflicts..."
	@./scripts/cleanup-zombies.sh

docker_safe_restart:
	@echo "Performing safe restart with cleanup..."
	@./scripts/safe-restart.sh

# ============================================
# Kubernetes Commands
# ============================================

k8s_deploy:
	@echo "Deploying to Kubernetes with Fabric8..."
	./deploy-k8s.sh

k8s_undeploy:
	@echo "Removing from Kubernetes..."
	./undeploy-k8s.sh

k8s_status:
	@echo "=== Pods ==="
	kubectl get pods -n basestation-platform
	@echo ""
	@echo "=== Services ==="
	kubectl get services -n basestation-platform
	@echo ""
	@echo "=== Deployments ==="
	kubectl get deployments -n basestation-platform

k8s_logs:
	kubectl logs -f -l app.kubernetes.io/part-of=basestation-platform -n basestation-platform --all-containers=true

k8s_init_db:
	@echo "Initializing all Kubernetes databases..."
	@./init-db/k8s-init-all-databases.sh

# ============================================
# Development Commands
# ============================================

build:
	@echo "Building all packages..."
	mvn clean package -DskipTests

test:
	@echo "Running all tests..."
	mvn test

clean:
	@echo "Cleaning build artifacts..."
	mvn clean
	@echo "Removing Docker volumes (optional)..."
	@read -p "Remove Docker volumes? [y/N]: " confirm; \
	if [ "$$confirm" = "y" ] || [ "$$confirm" = "Y" ]; then \
		docker compose down -v; \
	fi
