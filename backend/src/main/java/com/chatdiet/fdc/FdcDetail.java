package com.chatdiet.fdc;

import java.util.List;

/** A fetched food's per-100g nutrition plus its known real-world portions, from one detail-endpoint call. */
public record FdcDetail(FdcProduct product, List<FdcPortion> portions) {
}
