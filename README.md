# Plane Calculator

Protótipo de uma aplicação web para receber entradas, executar cálculos no backend e plotar os pontos resultantes em um plano cartesiano.

## Stack

- Frontend: Angular 21
- Backend: Spring Boot 3.5 / Java 17
- Persistência: nenhuma no protótipo
- Gráfico: SVG nativo

## Rodando o backend

```bash
cd backend
mvn spring-boot:run
```

Backend:
http://localhost:8080

## Rodando o frontend

Pré-requisito: Node.js e npm.

```bash
cd frontend
npm install
npm start
```

Frontend:
http://localhost:4200

## Fluxo

1. Angular coleta os pontos.
2. POST para `POST /api/calculations`.
3. Spring executa `CalculationService`.
4. Backend devolve os pontos calculados.
5. Angular plota os pontos no SVG.

## Onde colocar a regra real

Edite:

`backend/src/main/java/com/example/planecalculator/service/CalculationService.java`

O método `calculate()` é o lugar onde a fórmula/regra do seu sistema deve entrar.

## Próximos passos sugeridos

- Definir os inputs reais.
- Implementar a fórmula de cálculo.
- Melhorar escala e marcações dos eixos.
- Adicionar linhas entre pontos, se necessário.
- Adicionar zoom/pan.
- Adicionar validações.
- Só depois decidir se precisa de PostgreSQL.
