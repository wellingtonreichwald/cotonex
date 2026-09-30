# CoToneX — gerar o APK sem instalar nada

Você precisa de **uma conta GitHub (grátis)** e do navegador. Nada de Android
Studio, Gradle ou SDK no seu PC.

O arquivo que você vai subir é **`CoToneX-para-github.zip`** (está nesta mesma
pasta). Ele traz só o que o build precisa: `.github/`, `android/` e este
passo a passo. Já ficaram de fora `local.properties`, `.gradle`, `.idea` e
qualquer `build/` — coisa que só serve no seu computador.

---

## 1. Crie o repositório

1. Abre <https://github.com/new>
2. **Repository name**: `cotonex` (ou o nome que você quiser)
3. **Public** ou **Private**: tanto faz — os dois compilam de graça
4. Deixa **DESMARCADO** o "Add a README file"
5. Clica em **Create repository**

## 2. Solta os arquivos

1. Descompacta o `CoToneX-para-github.zip` em qualquer pasta
2. Na página do repositório novo, clica em **Add file → Upload files**
3. Arrasta as duas pastas (`.github` e `android`) e o `LEIA-ME-APK.md`
4. Clica em **Commit changes**

## 3. Deixa compilar

1. Abre a aba **Actions** (fica no topo do repositório)
2. Aparece o workflow **APK** rodando — clica nele para acompanhar
3. A primeira vez leva de **3 a 6 minutos** (baixa Gradle, Android SDK e as
   bibliotecas do Compose)
4. Quando fica com **check verde**, terminou

## 4. Baixa o APK

1. Clica no workflow verde
2. Descendo na página, em **Artifacts**, clica em **CoToneX-debug**
3. Baixa um `.zip` — dentro dele está o **`app-debug.apk`**
   (o GitHub empacota o artefato; é só descompactar)

## 5. Instala no celular

1. Passa o `app-debug.apk` para o celular (cabo USB, Google Drive, WhatsApp,
   e-mail — o que for mais rápido)
2. No celular: **Configurações → Segurança (ou Aplicativos) → Instalar apps
   desconhecidos** → libera o app que vai abrir o arquivo (Files, Navegador,
   Drive)
3. Toca no `app-debug.apk` → **Instalar**

---

## Depois que você mexer no código

É só subir os arquivos de novo (ou fazer um push) e o **Actions roda sozinho**;
sempre sai um APK novo no mesmo lugar.

- Se a aba **Actions** não aparecer: **Settings → Actions → General →
  Allow all actions** → Save.
- O workflow é o arquivo `.github/workflows/apk.yml`; ele usa JDK 17, Android
  SDK platform 35 e Gradle 8.11.1 — a mesma versão que o projeto pede.
- Botou o celular no USB com **depuração USB** ligada? No Android Studio dá
  para instalar direto, mas pelo Actions o caminho é o passo 5 acima.
