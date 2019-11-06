CREATE TABLE IF NOT EXISTS "metadata" (
    "id" INTEGER  NOT NULL,
    "version" INTEGER  NOT NULL DEFAULT 1 UNIQUE,
    "timestamp" INTEGER  NOT NULL,
    PRIMARY KEY ("id")
);