(function() {
    'use strict';

    angular.module('theHiveComponents')
        .component('logDatalist', {
            controller: function() {

                // this.updateField = function(newValue) {
                //     this.onUpdate({
                //         fieldName: ['customFields', this.field.reference, this.field.type].join('.'),
                //         value: newValue
                //     });
                // };

            },
            controllerAs: '$cmp',
            templateUrl: 'views/components/datalist/log.datalist.component.html',
            bindings: {
                data: '<'
            }
        });
})();
