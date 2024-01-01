
// UnderwayProjectRquest.java

package com.cbq.yatori.core.action.enaea.entity.underwayproject;

import com.fasterxml.jackson.annotation.*;

/**
 * Request
 */
@lombok.Data
public class UnderwayProjectRquest {
    @lombok.Getter(onMethod_ = {@JsonProperty("id")})
    @lombok.Setter(onMethod_ = {@JsonProperty("id")})
    private long id;
    @lombok.Getter(onMethod_ = {@JsonProperty("result")})
    @lombok.Setter(onMethod_ = {@JsonProperty("result")})
    private Result result;
    @lombok.Getter(onMethod_ = {@JsonProperty("totalCount")})
    @lombok.Setter(onMethod_ = {@JsonProperty("totalCount")})
    private long totalCount;
}