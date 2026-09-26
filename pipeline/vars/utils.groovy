def getGitBranchName() {
    def branch_name = GIT_BRANCH
    if (branch_name.contains("origin/")) {
        branch_name = branch_name.split("origin/")[1]
    }
    return branch_name
}

def setGitBranchName() {
    def branch_name = GIT_BRANCH
    def commitSha = GIT_COMMIT.take(8)
    if (branch_name.contains("origin/")) {
        branch_name = branch_name.split("origin/")[1]
    }
    return (branch_name == "develop") ? "${branch_name}-${commitSha}" : branch_name
}

def setGitBranchNameDockerfile() {
    def branch_name = GIT_BRANCH
    if (branch_name.contains("origin/")) {
        branch_name = branch_name.split("origin/")[1]
    }
    return (branch_name == "develop") ? "Dockerfile.test" : "Dockerfile"
}
