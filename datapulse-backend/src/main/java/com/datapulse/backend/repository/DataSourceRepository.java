package com.datapulse.backend.repository;

import com.datapulse.backend.model.DataSource;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DataSourceRepository extends JpaRepository<DataSource, String> {
}
