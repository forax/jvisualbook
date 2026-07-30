import { mkdirSync, rmSync, cpSync } from 'node:fs';

mkdirSync('public', { recursive: true });
rmSync('public/vs', { recursive: true, force: true });
cpSync('node_modules/monaco-editor/min/vs', 'public/vs', { recursive: true });
