import type { ErrorObject } from "../types";
export declare class ValidationError extends Error {
    readonly errors: Partial<ErrorObject>[];
    readonly ajv: true;
    readonly validation: true;
    constructor(errors: Partial<ErrorObject>[]);
}
export declare class MissingRefError extends Error {
    readonly missingRef: string;
    readonly missingSchema: string;
    constructor(baseId: string, ref: string, msg?: string);
}
