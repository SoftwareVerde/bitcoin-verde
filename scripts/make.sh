#!/bin/bash

./scripts/clean.sh

rm -rf out

./scripts/make-jar.sh

# Copy Config
mkdir -p out/conf
cp -R conf/* out/conf/.

# Create Run-Script
echo -e "#!/bin/bash\n\nexec java -jar bin/main.jar \"conf/server.conf\"\n" > out/run.sh
chmod 770 out/run.sh

