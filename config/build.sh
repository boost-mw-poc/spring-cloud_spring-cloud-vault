#!/bin/bash

./src/test/bash/install_vault.sh
./src/test/bash/create_certificates.sh
./src/test/bash/local_run_vault.sh &
./mvnw -s .settings.xml clean install -Pdocs ${@}
pkill -f "vault server"
