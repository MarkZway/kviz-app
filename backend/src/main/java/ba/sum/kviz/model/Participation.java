package ba.sum.kviz.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "participation")
@Getter
@Setter
@NoArgsConstructor
public class Participation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "quiz_id", nullable = false)
    private Quiz quiz;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id")
    private Team team;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ParticipationStatus status = ParticipationStatus.IN_PROGRESS;

    @Column(name = "current_question_index", nullable = false)
    private Integer currentQuestionIndex = 0;

    @Column(name = "current_question_started_at")
    private LocalDateTime currentQuestionStartedAt;

    @Column(name = "total_score", nullable = false)
    private Integer totalScore = 0;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt = LocalDateTime.now();

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @OneToMany(mappedBy = "participation", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SubmittedAnswer> answers = new ArrayList<>();
}