package com.tencent.supersonic.chat.core.mapper;

import java.util.Objects;
import lombok.Builder;
import lombok.Data;
import lombok.ToString;

@Data
@ToString
@Builder
public class MatchText {

    private String regText;

    private String detectSegment;

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        MatchText that = (MatchText) o;
        return Objects.equals(regText, that.regText) && Objects.equals(detectSegment, that.detectSegment);
    }

    @Override
    public int hashCode() {
        return Objects.hash(regText, detectSegment);
    }
}
