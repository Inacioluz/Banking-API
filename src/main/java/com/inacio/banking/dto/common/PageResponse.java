package com.inacio.banking.dto.common;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

@Schema(name = "PageResponse", description = "Envelope de paginacao")
public record PageResponse<T>(

        @Schema(description = "Itens da pagina atual")
        List<T> content,

        @Schema(description = "Indice da pagina, base zero", example = "0")
        int page,

        @Schema(description = "Quantidade de itens por pagina", example = "20")
        int size,

        @Schema(description = "Total de itens encontrados", example = "137")
        long totalElements,

        @Schema(description = "Total de paginas", example = "7")
        int totalPages,

        @Schema(description = "Indica se esta e a ultima pagina", example = "false")
        boolean last
) {

    public static <E, T> PageResponse<T> of(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(
                page.getContent().stream().map(mapper).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isLast()
        );
    }
}
