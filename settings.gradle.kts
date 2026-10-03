pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    // No project may declare its own repositories: every artifact comes from these two (T13).
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    // Each group comes from exactly one repository (anti dependency-confusion); every artifact
    // is additionally SHA-256 pinned in gradle/verification-metadata.xml.
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google\\.android.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral {
            content {
                excludeGroupByRegex("com\\.android.*")
                excludeGroupByRegex("com\\.google\\.android.*")
                excludeGroupByRegex("androidx.*")
            }
        }
    }
}

rootProject.name = "offgrid-vault"

include(":app")
include(":core:crypto")
include(":core:vault")
include(":autofill")
