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
                this.removeCustomField = function(id) {
                    this.onRemove({
                        fieldId: id
                    })
                }
            },
            controllerAs: '$ctrl',
            templateUrl: 'views/components/common/custom-field-input.component.html',
            bindings: {
                index: '<',
                field: '<',
                value: '=',
                id: '<',
                onUpdate: '&',
                onRemove: '&',
                editable: '<',
                removable: '<'
            }
        });
})();
