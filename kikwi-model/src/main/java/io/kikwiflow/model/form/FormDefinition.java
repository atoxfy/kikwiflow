package io.kikwiflow.model.form;

import java.util.List;

public record FormDefinition (
         String id,
         String key,
         String title,
         String description,
         List<FormComponentDefinition> components){
}
