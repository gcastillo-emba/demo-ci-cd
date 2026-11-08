#!/usr/bin/env node
// Reenvía los argumentos al Maven Wrapper del backend (mvnw.cmd en Windows, ./mvnw en el resto).
// Uso: node scripts/mvn.mjs -B -ntp verify
import { mvnw } from './lib/run.mjs';

process.exit(mvnw(process.argv.slice(2)));
