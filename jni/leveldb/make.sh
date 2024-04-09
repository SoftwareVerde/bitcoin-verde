gcc -c com_google_leveldb_NativeLevelDb.c -I/opt/homebrew/Cellar/openjdk@17/17.0.10/include -Iinclude
gcc -shared com_google_leveldb_NativeLevelDb.o libleveldb.1.23.0.dylib -o leveldb-jni.dylib
cp leveldb-jni.dylib ../../src/main/resources/lib/.
