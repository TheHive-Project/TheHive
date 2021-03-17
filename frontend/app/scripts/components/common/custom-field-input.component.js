(function() {
    'use strict';

    angular.module('theHiveComponents')
        .component('customFieldInput', {
            controller: function() {
                this.updateField = function(newValue) {
                    this.onUpdate({
                        fieldName: ['customFields', this.field.reference, this.field.type].join('.'),
                        value: newValue
                    });
                };
                this.removeCustomField = function(fieldId) {
                    this.onRemove({
                        fieldId: fieldId
                    })
                }
            },
            controllerAs: '$ctrl',
            templateUrl: 'views/components/common/custom-field-input.component.html',
            bindings: {
                index: '<',
                field: '<',
                value: '=',
                onUpdate: '&',
                onRemove: '&',
                editable: '<',
                removable: '<'
            }
        });
})();
