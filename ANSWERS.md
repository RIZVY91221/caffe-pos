# Answers

All numbers below are produced directly by `PricingEngine.kt` via `AppendixBSeedTest`.

Run the test that prints and verifies Q1–Q4 and Q6:
```bash
./gradlew test --tests "com.devsstream.caffepos.AppendixBSeedTest"
```

---

## Q1. Payable of each Appendix B order (O1–O6) and day's total

| Order | Payable (BDT) | Payable (poisha) |
|-------|---------------|------------------|
| O1 | 347 BDT | 34,700 |
| O2 | 876 BDT | 87,600 |
| O3 | 988 BDT | 98,800 |
| O4 | 1,213 BDT | 121,300 |
| O5 | 520 BDT | 52,000 |
| O6 | 970 BDT | 97,000 |
| **Day's total** | **4,914 BDT** | **491,400** |

---

## Q2. Cash total and card total for 20 November 2026

- **Card total:** `500` (O3 split) + `876` (O2) + `1,213` (O4) = **2,589 BDT** (`258,900` poisha)
- **Cash total:** `347` (O1) + `488` (O3 split remainder) + `520` (O5) + `970` (O6) = **2,325 BDT** (`232,500` poisha)
- Check: `2,589 + 2,325 = 4,914 BDT`

---

## Q3. Top item by units sold

- **Croissant:** **9 units** (1 in O1 + 2 in O4 + 6 in O6; includes the 2 free bakery units from P1 3-for-2 in O6).

---

## Q4. Step-by-step working for O3 (16:59, `DEVS10`)

Items: `Iced Tea Large x3`, `Chicken Sandwich x2`.

1. **Line totals:**
   - Iced Tea Large unit = `120 + 30 = 150 BDT` (`15,000` poisha) -> `x3 = 450.00 BDT` (`45,000` poisha)
   - Chicken Sandwich unit = `320 BDT` (`32,000` poisha) -> `x2 = 640.00 BDT` (`64,000` poisha)
   - Raw sum = `1,090.00 BDT` (`109,000` poisha)
2. **P1 Bakery 3-for-2:** 0 bakery units -> `0.00 BDT`
3. **P2 Happy hour (`16:59` is `< 17:00:00`):**
   - `10%` off Iced Tea line (`45,000` poisha): `(45000 * 10 + 50) / 100 = 4,500` poisha (`45.00 BDT`)
4. **Subtotal:** `109,000 - 4,500 = 104,500` poisha (`1,045.00 BDT`)
5. **Order discount:**
   - T1 (`subtotal >= 1,000 BDT`) = `8,000` poisha (`80.00 BDT`)
   - C1 (`DEVS10`, `10%` of `104,500`, cap `12,000`): `(104500 * 10 + 50) / 100 = 10,450` poisha (`104.50 BDT`)
   - C1 wins (`104.50 > 80.00`). Discounted subtotal = `104,500 - 10,450 = 94,050` poisha (`940.50 BDT`)
6. **VAT 5% and Payable:**
   - `5%` of `94,050` = `4,702.5` poisha -> half-up to nearest poisha: `(94050 * 5 + 50) / 100 = 4,703` poisha (`47.03 BDT`)
   - Total before final rounding = `94,050 + 4,703 = 98,753` poisha (`987.53 BDT`)
   - Half-up to whole BDT: `(98753 + 50) / 100 * 100 = 98,800` poisha = **988 BDT**

---

## Q5. Card payment of 1,077 BDT second by second

1. **0s:** `CartViewModel.payCard()` creates `cardRef` (UUID) in `SavedStateHandle`, sets UI state to `CARD_WAITING`, and calls `CardTerminalWrapper.charge(107_700L, cardRef)`.
2. **0s:** `LegacyCardTerminal.charge()` increments `attempts(cardRef)` to `1`, sees `tail == 77`, marks `status[cardRef] = "APPROVED"`, and returns without scheduling any callback.
3. **0s–15s:** `withTimeoutOrNull(15_000L)` suspends waiting for `Callback.onApproved` / `onDeclined`.
4. **15s:** The 15-second timeout expires and returns `null`. `CardTerminalWrapper` immediately calls `terminal.queryStatus(cardRef)`, which returns `"APPROVED"`.
5. **15s:** `CardTerminalWrapper` returns `CardResult.Approved`, `CartViewModel` writes the order + payment to Room in one transaction, clears the cart, and shows the receipt.
6. **Charges at terminal afterwards:** Exactly **1** (`terminal.attempts(cardRef) == 1`).

---

## Q6. Adding one more Brownie to TC-06

- **Payable:** **1,216 BDT** (`121,600` poisha).
- **Working:** Adding 1 Brownie (`120 BDT`) raises the raw total from `1,360` to `1,480 BDT`. P1 remains `120 BDT` and P2 remains `82 BDT`, so `subtotal = 1,480 - 120 - 82 = 1,278 BDT`. C1 (`10%` of `1,278` = `127.80`) hits the `120 BDT` cap, leaving `1,158 BDT`. VAT `5%` = `57.90 BDT`. Total = `1,215.90 BDT`, rounded half-up to **1,216 BDT**.
- **Why P1 did not change:** TC-06 already had 4 bakery units (2 Croissants + 2 Brownies), where `floor(4 / 3) = 1` free unit. Adding 1 Brownie makes 5 bakery units, and `floor(5 / 3)` is still `1`. A second free bakery unit only triggers at 6 bakery units.
