package com.kaushiksridhar.finledger.importing;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A line in an uploaded file that couldn't be read. */
@Entity
@Table(name = "import_row_errors")
@Getter
@Setter
@NoArgsConstructor
public class ImportRowError {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "batch_id", nullable = false)
    private ImportBatch batch;

    @Column(name = "line_number", nullable = false)
    private int lineNumber;

    @Column(nullable = false, length = 255)
    private String message;

    public ImportRowError(ImportBatch batch, int lineNumber, String message) {
        this.batch = batch;
        this.lineNumber = lineNumber;
        this.message = message.length() <= 255 ? message : message.substring(0, 255);
    }
}
