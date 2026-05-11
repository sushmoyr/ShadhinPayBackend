package com.shadhinpay.ledger.usecase.impl;

/**
 * Routes a source identifier to one of the 10 hot-account shards seeded by V1003. Uses {@link
 * Math#floorMod(int, int)} so that {@code Integer.MIN_VALUE.hashCode()} does not collapse to a
 * negative shard id (the trap with {@link Math#abs(int)}).
 */
final class LedgerShardSelector {

  static final int SHARD_COUNT = 10;

  private LedgerShardSelector() {}

  static int selectShard(String sourceId) {
    return Math.floorMod(sourceId.hashCode(), SHARD_COUNT);
  }
}
