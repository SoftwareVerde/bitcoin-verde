#!/bin/bash

BOOTSTRAP_LOCATION='../bitcoin-verde-bootstrap'

if [[ ! -d "${BOOTSTRAP_LOCATION}" ]]; then
    echo "Bootstrap directory not found at ${BOOTSTRAP_LOCATION}" 1>&2
    exit 1
fi

echo -n "Version: "
read VERSION

rm -rf release 2>/dev/null
mkdir release

git clone https://github.com/softwareverde/bitcoin-verde.git release
cd release

git checkout "${VERSION}"

# Build the jar.
./scripts/make.sh


# Copy the bootstrap data.
cd -

cp "${BOOTSTRAP_LOCATION}/bootstrap-8.tar.gz" release/out/.
cp "${BOOTSTRAP_LOCATION}/bootstrap-9.tar.gz" release/out/.

cd -
cd out

tar xzf bootstrap-8.tar.gz
tar xzf bootstrap-9.tar.gz

rm bootstrap-8.tar.gz
rm bootstrap-9.tar.gz


# Copy the OSX OpenSSL script.
mkdir osx
cp ../scripts/osx/link-openssl.sh osx/.


# Create the tarball.
cd ..
mv out "bitcoin-verde-${VERSION}"
tar czf "bitcoin-verde-${VERSION}.tar.gz" "bitcoin-verde-${VERSION}"
mv "bitcoin-verde-${VERSION}.tar.gz" ../.


