#!/bin/bash

echo -e "#!/bin/bash\n\nexec java -jar bin/main.jar \"\$@\"\n" > out/run.sh
echo -e "#!/bin/bash\n\nexec java -d64 -Xms2G -Xmx8G -jar bin/main.jar \"NODE\" \"conf/server.conf\"\n" > out/run-node.sh
echo -e "#!/bin/bash\n\nexec java -jar bin/main.jar \"EXPLORER\" \"conf/server.conf\"\n" > out/run-explorer.sh
echo -e "#!/bin/bash\n\nexec java -jar bin/main.jar \"WALLET\" \"conf/server.conf\"\n" > out/run-wallet.sh
echo -e "#!/bin/bash\n\nexec java -jar bin/main.jar \"VALIDATE\" \"conf/server.conf\" \"\$1\"\n" > out/run-validation.sh
echo -e "#!/bin/bash\n\nexec java -jar bin/main.jar \"REPAIR\" \"conf/server.conf\" \"\$@\"\n" > out/run-repair.sh
echo -e "#!/bin/bash\n\nexec java -jar bin/main.jar \"MIGRATION\" \"conf/server.conf\"\n" > out/run-migration.sh
echo -e "#!/bin/bash\n\nexec java -jar bin/main.jar \"STRATUM\" \"conf/server.conf\"\n" > out/run-stratum.sh
echo -e "#!/bin/bash\n\nexec java -jar bin/main.jar \"DATABASE\" \"conf/server.conf\"\n" > out/run-database.sh
echo -e "#!/bin/bash\n\nexec java -jar bin/main.jar \"ADDRESS\"\n" > out/run-address.sh
echo -e "#!/bin/bash\n\nexec java -jar bin/main.jar \"MINER\"\n" > out/run-miner.sh
echo -e "#!/bin/bash\n\necho -n \"Previous Block Hash: \"\nread previous_block_hash\n\necho -n \"Pay-To Address: \"\nread bitcoin_address\n\necho -n \"CPU Thread Count: \"\nread cpu_thread_count\n\necho -n \"GPU Thread Count: \"\nread gpu_thread_count\necho\n\nexec java -jar bin/main.jar \"MINER\" \"\${previous_block_hash}\" \"\${bitcoin_address}\" \"\${cpu_thread_count}\" \"\${gpu_thread_count}\"\n" > out/run-miner.sh
chmod 770 out/*.sh

