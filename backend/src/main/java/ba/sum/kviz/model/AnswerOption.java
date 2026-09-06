package ba.sum.kviz.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "answer_option")
@Getter
@Setter
@NoArgsConstructor
public class AnswerOption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id", nullable = false)
    private Question question;

    @Column(nullable = false, length = 500)
    private String text;

    @Column(name = "is_correct", nullable = false)
    private boolean correct = false;

    @Column(name = "order_index", nullable = false)
    private Integer orderIndex;
}