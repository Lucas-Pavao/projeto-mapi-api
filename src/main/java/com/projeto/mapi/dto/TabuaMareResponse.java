package com.projeto.mapi.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TabuaMareResponse<T> {
    private T data;
    private Integer total;
    private TabuaMareError error;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TabuaMareError {
        private String msg;
        private Integer code;
    }
}
