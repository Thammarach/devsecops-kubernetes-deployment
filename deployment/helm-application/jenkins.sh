#!/bin/bash

export WORKSPACE=$1
export PROJECT_NANE=$2
export PATH=$WORKSPACE"/"$PROJECT_NANE
export TAG=$3
export DEPLOYMENT_PATH=$PATH
echo "PATH : $PATH"
echo "change version $TAG"
    if [[ "$TAG" =~ ^v-[0-9]{1,}\.[0-9]{1,}\.[0-9]{1,}$ ]]; then
        echo "PRD";
        /usr/bin/sed -i "s/tag: v-[0-9]\+\.[0-9]\+\.[0-9]\+/tag: $TAG/" $PATH/values/prd.yaml
        /bin/git config --global user.email "admin@domain.com"
        /bin/git config --global user.name "admin"
        /bin/git config pull.rebase false
        /bin/git fetch --all
        /bin/git pull origin main
        /bin/git add . && /bin/git commit -am "new project $PROJECT_NANE" && /bin/git push origin main
        echo "Directory $DEPLOYMENT_PATH already exists. Navigating to it.";
    elif [[ "$TAG" =~ ^develop-[a-z0-9]{8}$ ]]; then
        echo "DEVELOP";
        /usr/bin/sed  -i "s|tag: develop-[a-z0-9]\{8\}|tag: $TAG|g" $PATH/values/dev.yaml
        /bin/git config --global user.email "admin@domain.com"
        /bin/git config --global user.name "admin"
        /bin/git add . && /bin/git commit -am "new project $PROJECT_NANE" && /bin/git push origin main
        echo "Directory $DEPLOYMENT_PATH already exists. Navigating to it.";
    else
        echo "❌ Error: TAG ต้องขึ้นต้นด้วย 'v-' หรือ 'develop-' เท่านั้น"
        exit 1
    fi

