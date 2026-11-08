package com.emba.asuntoshoras.application.port.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.emba.asuntoshoras.domain.shared.InvalidValueException;
import com.emba.asuntoshoras.domain.user.Role;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PageQueryTest {

  @Test
  void defaultsArePageZeroAndSizeTwenty() {
    assertThat(PageQuery.of(null, null)).isEqualTo(new PageQuery(0, 20));
    assertThat(PageQuery.of(2, 100)).isEqualTo(new PageQuery(2, 100));
  }

  // Scenario: Página negativa
  @Test
  void negativePageIsInvalid() {
    assertThatThrownBy(() -> new PageQuery(-1, 20))
        .isInstanceOf(InvalidValueException.class)
        .extracting(e -> ((InvalidValueException) e).field())
        .isEqualTo("page");
  }

  // Scenario: Tamaño de página mayor al máximo
  @Test
  void sizeOutsideOneToHundredIsInvalid() {
    assertThatThrownBy(() -> new PageQuery(0, 101))
        .isInstanceOf(InvalidValueException.class)
        .extracting(e -> ((InvalidValueException) e).field())
        .isEqualTo("size");
    assertThatThrownBy(() -> new PageQuery(0, 0)).isInstanceOf(InvalidValueException.class);
  }

  @Test
  void pageResultComputesTotalPagesAndMaps() {
    PageResult<Integer> result = PageResult.of(List.of(1, 2), new PageQuery(1, 10), 25);

    assertThat(result.totalPages()).isEqualTo(3);
    assertThat(PageResult.of(List.of(), new PageQuery(0, 10), 0).totalPages()).isZero();
    assertThat(PageResult.of(List.of(), new PageQuery(0, 10), 20).totalPages()).isEqualTo(2);
    PageResult<String> mapped = result.map(i -> "#" + i);
    assertThat(mapped.items()).containsExactly("#1", "#2");
    assertThat(mapped.totalItems()).isEqualTo(25);
    assertThat(mapped.page()).isEqualTo(1);
  }

  @Test
  void currentUserKnowsIfPartner() {
    UUID id = UUID.randomUUID();
    assertThat(new CurrentUser(id, Role.PARTNER).isPartner()).isTrue();
    assertThat(new CurrentUser(id, Role.LAWYER).isPartner()).isFalse();
    assertThatThrownBy(() -> new CurrentUser(null, Role.LAWYER))
        .isInstanceOf(NullPointerException.class);
  }
}
