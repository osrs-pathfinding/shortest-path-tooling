import Ajv2020 from "ajv/dist/2020";
import schema from "../generated/account-build-v1.schema.json";
import type { AccountBuild } from "./contracts";

const validate = new Ajv2020({ allErrors: true }).compile<AccountBuild>(schema);

export function validateAccount(value: unknown): AccountBuild {
  if (validate(value)) return value;
  const error = validate.errors?.[0];
  throw new Error(error ? `${error.instancePath || "Account"} ${error.message}` : "Invalid account build");
}
