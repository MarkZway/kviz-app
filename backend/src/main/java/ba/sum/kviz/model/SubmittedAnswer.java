package ba.sum.kviz.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "submitted_answer")
@Getter
@Setter
@NoArgsConstructor
public class SubmittedAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "participation_id", nullable = false)
    private Participation participation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id", nullable = false)
    private Question question;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "selected_option_id")
    private AnswerOption selectedOption;

    @Column(name = "text_answer", length = 500)
    private String textAnswer;

    @Column(name = "is_correct", nullable = false)
    private boolean correct;

    @Column(name = "time_taken_ms", nullable = false)
    private Long timeTakenMs;

    @Column(name = "points_awarded", nullable = false)
    private Integer pointsAwarded;

    @Column(name = "answered_at", nullable = false)
    private LocalDateTime answeredAt = LocalDateTime.now();
}