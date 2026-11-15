-- Etapa 8 do IN1165: a previsão gravada passa a carregar a proveniência do modelo que a emitiu.
-- ADITIVA: só ADD COLUMN, anulável. Linhas antigas ficam com NULL (sem informação, não "medido").
--
-- Por quê: as leituras (GET) remontam a previsão a partir desta tabela, sem chamar a IA. Sem gravar a fonte da chuva,
-- o tipo de dado de treino e o aviso de sintético, um risco vindo de modelo treinado com rótulo sintético voltaria ao
-- usuário sem etiqueta.
-- model_info = {"rainSource", "trainingData", "syntheticWarning", "riskThresholds": {"medium", "high", "source"}}
ALTER TABLE flood_predictions ADD COLUMN model_info JSONB;
