(function() {
    'use strict';

    angular.module('theHiveComponents')
        .component('logDatalist', {
            controller: function() {

                this.isImage = function(contentType) {
                    return angular.isString(contentType) && contentType.indexOf('image') === 0;
                };

            },
            controllerAs: '$cmp',
            templateUrl: 'views/components/datalist/log.datalist.component.html',
            bindings: {
                data: '<'
            }
        });
})();
