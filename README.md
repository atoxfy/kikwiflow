

<h1 align="center">kikwiflow </h1>
<p align="center">
<img alt="Build Status" src="https://img.shields.io/badge/build-passing-34d399?style=for-the-badge">
<img alt="License" src="https://img.shields.io/badge/license-Apache%202.0-0ea5e9?style=for-the-badge">
<img alt="Version" src="https://img.shields.io/badge/version-0.1.0--SNAPSHOT-4c1d95?style=for-the-badge">
</p>

<h3 align="center">Você não precisa ser um especialista em BPPMN, para construir processos eficientes, observáveis e seguros com Java!</h3>


Kikwiflow é um motor orquestrador de fluxos construído do zero para resolver as dores crônicas das plataformas tradicionais. Baseado na robustez do ecossistema Java e projetado para arquiteturas modernas de microserviços, oferece execução segura, observável, auditável e altamente performática de processos de negócio.

## ✨ Por que Kikwiflow?

### 🎯 **Developer Experience First**

- **Time to First Commit**: Acreditamos que qualquer desenvolvedor Java pode criar fluxos sem especialização em notações complexas como BPMN e sem precisar especializar-se em um vendor de BPMS (vendor free).
- **Código limpo**: Elimina linguagens de expressão inseguras (SPEL/JUEL) em favor de classes Java puras. 
- **Integração Spring Boot nativa**: Configuração zero com auto-discovery de delegates e regras
- **Independencia de mantenedora**: Por se tratar de um código Java moderno, diferente de algumas soluções que a pesar de open-source quase ninguém entende o código (funciona, mas como?), aqui qualquer desenvolvedor pode fazer o fork do projeto e customiza-lo a seu bel-prazer.  

### 🔒 **Segurança por Design**
- **RBAC nativo**: Cada variável de processo possui controle granular de acesso baseado em roles
- **Validação em deploy**: Impede a implantação de processos com referências quebradas
- **LGPD/GDPR ready**: Privacidade de dados desde a concepção

### ⚡ **Performance Extrema**
- **Java 21+ Virtual Threads**: Escalabilidade massiva com recursos mínimos
- **CQRS nativo**: Separação otimizada entre operações de comando e consulta
- **Execução assíncrona inteligente**: Pontos de commit configuráveis para máxima eficiência

## 🏗️ Arquitetura Moderna

### Filosofia de Três Pilares

```
🔧 EXECUTABLE TASKS                 ⏳ EXTERNAL TASKS              🤔 DECISION RULES
   (Automatização, fazer algo)      (Trabalho externo, aguardar)    (Tomada de decisão)
```

Qualquer processo complexo pode ser decomposto nestes três tipos fundamentais de passos, proporcionando uma abstração poderosa e intuitiva.

### Modular por Natureza

```
┌─────────────────────────────────────────────────────────┐
│                    🎮 STARTERS                          │
│  kikwi-spring-boot-starter, kikwi-query-starter         │
├─────────────────────────────────────────────────────────┤
│                 🔧 AUTO CONFIGURE                       │
│     Integração transparente com Spring Boot             │
├─────────────────────────────────────────────────────────┤
│                   💎 CORE ENGINE                        │
│   Execução, navegação, continuidade assíncrona          │
├─────────────────────────────────────────────────────────┤
│                   📋 API CONTRACTS                      │
│    Interfaces estáveis e bem definidas                  │
├─────────────────────────────────────────────────────────┤
│                   🔌 ADDONS PLUGÁVEIS                   │
│  In-Memory, MongoDB, REST API, Observabilidade          │
└─────────────────────────────────────────────────────────┘
```



## 🛠️ Ecossistema de Addons

- **`kikwi-management-rest`**: API REST completa para gerenciamento
- **`kikwi-in-memory-addons`**: Persistência em memória para testes

## 🤝 Contribuindo

Ainda estamos estruturando o processo de contribuição, mas o Kikwiflow é um projeto de código aberto e comunidade-driven. Contribuições são bem-vindas! Abaixo você vai encontrar nossos canais e será um prazer trocar conhecimento. Se quiser, inicie dando uma estrela ao projeto! 

```bash
git clone https://github.com/kikwiflow/kikwiflow.git
cd kikwiflow
./mvnw clean install
```



<h2>Kikwiflow Titans ❤️ Bootstrap Team </h2> 

Um projeto desta magnitude não sairia do papel sem o apoio de algumas pessoas, então fica aqui um agradecimento especial as que
marcaram o kikwiflow quando ainda era somente um sonho distante. Seja por um incentivo, uma ideia, uma reclamação de outra ferramenta ou simplesmente por escutar: Obrigado!

[Audrey Behenck](https://github.com/audreybehenck)

Victória Behenck

[Leonardo Borges](https://github.com/LeonardoBorges)

[Marcus Vinicius](https://github.com/markinog)

[Pietro Bucker](https://github.com/PietroBucker)

[Rebeca](https://github.com/rebecamontag)

[Irineu Artur](https://github.com/IAPdeS)

Max 

Murilo Rech

[Jean Robert](https://github.com/jradesenv)

[Lucas Silveira](https://github.com/lucascsilveira88)




## 📜 Licença

Apache License 2.0 - Veja [LICENSE](LICENSE) para detalhes.

## 🌟 Comunidade

- 💬 [Discord](https://discord.gg/5YrE84TsYM)
- 📧 [Email](mailto:kikwiflow@atoxfy.com)
- 🐛 [Issues](https://github.com/atoxfy/kikwiflow/issues)
- 📦 [Pacotes](https://github.com/orgs/atoxfy/packages?repo_name=kikwiflow)
- 👩‍💻 [Forúm](https://github.com/atoxfy/kikwiflow/discussions)

---

**Kikwiflow: Onde performance encontra simplicidade. Onde segurança encontra produtividade.**

*Construído por desenvolvedores, para desenvolvedores Java.* ☕

<h2>Apoio</h2>

[Kikwiflow](https://kikwiflow.io) é um projeto open-source mantido pela [{Atoxfy}](https://atoxfy.com).
