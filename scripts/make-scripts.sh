#!/bin/bash

# This JVM GC parameters are a pretty healthy configuration with used-footprint staying below 1G.  Collection times were about 1.8ms long and occurred about once every 2 seconds.
# Additionally, the G1 collector does compacting quite well ensuring long-life stability, and collects most objects in parallel. G1 also shrinks the memory footprint when not in use.
# Setting -XX:InitiatingHeapOccupancyPercent to 20 increases the frequency that old-generation objects are attempted to be collected.
# Setting -XX:G1MixedGCLiveThresholdPercent to 50 to encourage collecting "old" data before expanding the heap.
JVM_PARAMS='-XX:+UseG1GC -XX:NewSize=128M -XX:MaxNewSize=128M -XX:+UnlockExperimentalVMOptions -XX:InitiatingHeapOccupancyPercent=20 -XX:G1OldCSetRegionThresholdPercent=90 -XX:G1MixedGCLiveThresholdPercent=50 -XX:MaxGCPauseMillis=1000'

echo -e "#!/bin/bash\n\nexec java -jar bin/main.jar \"\$@\"\n" > out/run.sh
echo -e "#!/bin/bash\n\nexec java ${JVM_PARAMS} -jar bin/main.jar \"NODE\" \"conf/server.conf\"\n" > out/run-node.sh
echo -e "#!/bin/bash\n\nexec java ${JVM_PARAMS} -jar bin/main.jar \"EXPLORER\" \"conf/server.conf\"\n" > out/run-explorer.sh
echo -e "#!/bin/bash\n\nexec java -jar bin/main.jar \"SPV\" \"conf/server.conf\"\n" > out/run-spv.sh
echo -e "#!/bin/bash\n\nexec java -jar bin/main.jar \"WALLET\" \"conf/server.conf\"\n" > out/run-wallet.sh
echo -e "#!/bin/bash\n\nexec java -jar bin/main.jar \"VALIDATE\" \"conf/server.conf\" \"\$1\"\n" > out/run-validation.sh
echo -e "#!/bin/bash\n\nexec java -jar bin/main.jar \"STRATUM\" \"conf/server.conf\"\n" > out/run-stratum.sh
echo -e "#!/bin/bash\n\nexec java -jar bin/main.jar \"PROXY\" \"conf/server.conf\"\n" > out/run-proxy.sh
echo -e "#!/bin/bash\n\nexec java -jar bin/main.jar \"DATABASE\" \"conf/server.conf\"\n" > out/run-database.sh
echo -e "#!/bin/bash\n\nexec java -jar bin/main.jar \"ADDRESS\"\n" > out/run-address.sh
echo -e "#!/bin/bash\n\necho -n \"Address Prefix: \"\nread addressPrefix\necho -n \"Case insensitive? [true|FALSE] \"\nread caseInsensitive\n\nexec java -jar bin/main.jar \"ADDRESS\" \"\${addressPrefix}\" \"\${caseInsensitive}\"\n" > out/run-address.sh
echo -e "#!/bin/bash\n\nexec java -jar bin/main.jar \"SIGNATURE\" \"SIGN\" \"\$1\" \"\$2\" \"\$3\"\n" > out/run-signature.sh
echo -e "#!/bin/bash\n\nexec java -jar bin/main.jar \"SIGNATURE\" \"VERIFY\" \"\$1\" \"\$2\" \"\$3\"\n" > out/run-verify-signature.sh
echo -e "#!/bin/bash\n\nexec java -jar bin/main.jar \"ECIES\" \"ENCRYPT\"\n" > out/run-encrypt.sh
echo -e "#!/bin/bash\n\nexec java -jar bin/main.jar \"ECIES\" \"DECRYPT\"\n" > out/run-decrypt.sh
echo -e "#!/bin/bash\n\necho -n \"Thread Count: \"\nread cpu_thread_count\n\necho -n \"Prototype Block Data: \"\nread prototype_block_data\necho\n\nexec java -jar bin/main.jar \"MINER\" \"\${cpu_thread_count}\" \"\${prototype_block_data}\"\n" > out/run-miner.sh
echo -e "#!/bin/bash\n\n(echo '{\"method\":\"POST\",\"query\":\"SHUTDOWN\"}') | curl -s --data-binary @- localhost:8334\n" > out/shutdown.sh
echo -e "#!/bin/bash\n\nexec java -jar bin/main.jar \"CONFIGURATION\" \"conf/server.conf\"\n" > out/configure-node.sh

chmod 770 out/*.sh

mkdir out/rpc 2>/dev/null
cp scripts/rpc/*.sh out/rpc/.
chmod 770 out/rpc/*

