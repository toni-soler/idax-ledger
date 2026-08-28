import test from "node:test";
import assert from "node:assert/strict";
import { ledgerModule } from "../src/custom/index.js";
import {readFile}from"node:fs/promises";import{errorMessageKey,pollProof}from"../src/custom/ledgerRuntime.js";
const source=await readFile(new URL("../src/extension.jsx",import.meta.url),"utf8");const translations=await readFile(new URL("../src/locales/common.json",import.meta.url),"utf8");
test("exports an IDAX-native module, not an iframe", () => {
  assert.equal(ledgerModule.routePrefix, "/ledger");
  assert.equal(Object.hasOwn(ledgerModule, "iframe"), false);
});
test("routes all explorer and proof screens",()=>{for(const screen of ["networks","nodes","ledgers","transactions","proofs"])assert.match(source,new RegExp(screen))});
test("permission-gates create and verify",()=>{assert.match(source,/LEDGER_PROOF_CREATE/);assert.match(source,/LEDGER_PROOF_VERIFY/)});
test("renders semantic network health",()=>{assert.match(source,/status\.health/);assert.match(source,/healthyNodes/)});
test("renders proof list and cross-linked detail",()=>{assert.match(source,/function Proofs/);assert.match(source,/ledger\/transactions/);assert.match(source,/ledger\/ledgers/)});
test("supports JSON and precalculated-hash creation",()=>{assert.match(source,/RAW-BYTES-SHA256-V1/);assert.match(translations,/JCS/)});
test("maps idempotency conflict to a dedicated message",()=>assert.equal(errorMessageKey(409),"ledger.errors.idempotency"));
test("renders MATCH and ANCHOR_MISMATCH independently",()=>{assert.match(source,/integrity\.status/);assert.match(source,/ledger\.status/)});
test("polls until VALIDATED without creating another proof",async()=>{let calls=0;const result=await pollProof(async()=>({status:++calls===3?"VALIDATED":"SUBMITTED"}),"p",{intervalMs:1,timeoutMs:100});assert.equal(result.proof.status,"VALIDATED");assert.equal(calls,3)});
test("polling stops on timeout without marking failure",async()=>{const result=await pollProof(async()=>({status:"SUBMITTED"}),"p",{intervalMs:1,timeoutMs:2});assert.equal(result.timedOut,true);assert.equal(result.proof.status,"SUBMITTED")});
