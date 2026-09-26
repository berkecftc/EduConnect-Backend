variable "REGISTRY" {
  default = "educonnect"
}

variable "TAG" {
  default = "local"
}

variable "EXTRA_TAG" {
  default = ""
}

variable "REVISION" {
  default = ""
}

variable "ATTEST" {
  default = false
}

variable "MODULES" {
  default = [
    "config-server",
    "eureka-server",
    "api-gateway",
    "auth-services",
    "user-service",
    "club-service",
    "event-service",
    "course-service",
    "assignment-service",
    "post-service",
    "gamification-service",
    "notification-service",
    "llm-service",
  ]
}

group "default" {
  targets = ["service"]
}

target "service" {
  name       = module
  matrix     = { module = MODULES }
  context    = "."
  dockerfile = "Dockerfile"
  args       = { MODULE = module }
  attest     = ATTEST ? ["type=provenance,mode=max", "type=sbom"] : []
  tags = compact([
    "${REGISTRY}/${module}:${TAG}",
    notequal("", EXTRA_TAG) ? "${REGISTRY}/${module}:${EXTRA_TAG}" : "",
  ])
  labels = {
    "org.opencontainers.image.title"    = "educonnect-${module}"
    "org.opencontainers.image.source"   = "https://github.com/berkecftc/EduConnect-Backend"
    "org.opencontainers.image.revision" = REVISION
  }
}
